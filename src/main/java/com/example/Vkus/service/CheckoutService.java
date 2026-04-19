package com.example.Vkus.service;

import com.example.Vkus.entity.*;
import com.example.Vkus.repository.*;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.security.NotEnoughStockException;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class CheckoutService {

    private final CartService cartService;
    private final BuyerPricingService buyerPricingService;
    private final CartComboService cartComboService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderItemDiscountRepository orderItemDiscountRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final CurrentUserService currentUserService;
    private final EntityManager em;
    private final JdbcTemplate jdbc;

    public CheckoutService(
            CartComboService cartComboService,
            CartService cartService,
            BuyerPricingService buyerPricingService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            OrderItemDiscountRepository orderItemDiscountRepository,
            PaymentRepository paymentRepository,
            InventoryItemRepository inventoryItemRepository,
            CurrentUserService currentUserService,
            EntityManager em,
            JdbcTemplate jdbc
    ) {
        this.cartComboService = cartComboService;
        this.cartService = cartService;
        this.buyerPricingService = buyerPricingService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderItemDiscountRepository = orderItemDiscountRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.currentUserService = currentUserService;
        this.em = em;
        this.jdbc = jdbc;
    }

    @Transactional
    public Order checkout() {

        User user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        List<CartItem> cartItems = cartService.getItems(user.getId(), buffetId);
        var cartCombos = cartComboService.getCombos(user.getId(), buffetId);

        if (cartItems.isEmpty() && cartCombos.isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        Map<Long, Integer> needByProduct = new HashMap<>();
        for (CartItem ci : cartItems) needByProduct.merge(ci.getProduct().getId(), ci.getQty(), Integer::sum);

        for (var cc : cartCombos) {
            int comboQty = cc.getQty() == null ? 1 : cc.getQty();
            for (var it : cc.getItems()) {
                int itemQty = (it.getQty() == null ? 1 : it.getQty()) * comboQty;
                needByProduct.merge(it.getProduct().getId(), itemQty, Integer::sum);
            }
        }

        for (var e : needByProduct.entrySet()) {
            Long productId = e.getKey();
            int qty = e.getValue();

            InventoryItem inv = inventoryItemRepository
                    .findByBuffetIdAndProductId(buffetId, productId)
                    .orElseThrow(() -> new NotEnoughStockException("Нет inventory_items для productId=" + productId));

            Integer have = inv.getQuantity();
            if (have == null || have < qty) {
                throw new NotEnoughStockException(
                        "Недостаточно товара (productId=" + productId + "). На складе: " +
                                (have == null ? 0 : have) + ", нужно: " + qty
                );
            }
        }

        Buffet buffetRef = em.getReference(Buffet.class, buffetId);

        Order order = new Order();
        order.setUser(user);
        order.setBuffet(buffetRef);
        order.setStatus("created");
        order.setPickupCode(generatePickupCode());
        order.setPickupCodeExpiresAt(LocalDateTime.now().plusMinutes(30));
        order.setBuyerNameSnapshot(user.getFullName());
        order.setBuyerEmailSnapshot(user.getEmail());
        order.setBuffetNameSnapshot(buffetRef.getName());
        orderRepository.save(order);

        List<Product> products = cartItems.stream().map(CartItem::getProduct).toList();
        Map<Long, BuyerPricingService.Discounts> discountsMap =
                buyerPricingService.resolveDiscounts(user.getId(), buffetId, products);

        BigDecimal totalBase = BigDecimal.ZERO;
        BigDecimal totalDiscount = BigDecimal.ZERO;

        Set<Long> touchedBatchIds = new HashSet<>();

        for (CartItem ci : cartItems) {
            Product product = ci.getProduct();
            int qty = ci.getQty();
            BigDecimal unitBase = nz(product.getBasePrice());

            BuyerPricingService.Discounts d = discountsMap.get(product.getId());
            BigDecimal promo = d != null ? d.promoPercent() : null;
            BigDecimal monthly = d != null ? d.monthlyPercent() : null;

            List<BatchTake> takes = takeFromBatchesFefo(buffetId, product.getId(), qty);
            for (var t : takes) touchedBatchIds.add(t.batchId());

            PricingAgg agg = priceByTakes(unitBase, promo, monthly, takes);

            BigDecimal lineBase = unitBase.multiply(BigDecimal.valueOf(qty));
            BigDecimal lineFinal = agg.finalTotal();
            BigDecimal lineDiscount = lineBase.subtract(lineFinal);

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(product);
            oi.setProductNameSnapshot(product.getName());
            oi.setQty(qty);
            oi.setUnitPriceSnapshot(scale2(unitBase));
            oi.setDiscountAmount(scale2(lineDiscount.max(BigDecimal.ZERO)));
            oi.setFinalLineAmount(scale2(lineFinal.max(BigDecimal.ZERO)));
            orderItemRepository.save(oi);

            saveDiscountBreakdown(oi, product.getId(), agg);

            totalBase = totalBase.add(lineBase);
            totalDiscount = totalDiscount.add(lineDiscount);

            int updated = jdbc.update("""
                    UPDATE inventory_items
                    SET quantity = quantity - ?,
                        updated_at = now()
                    WHERE buffet_id = ? AND product_id = ? AND quantity >= ?
                    """, qty, buffetId, product.getId(), qty);

            if (updated == 0) {
                throw new IllegalStateException("Нельзя списать: общий остаток меньше списания или inventory_items нет");
            }

            for (BatchTake t : takes) {
                jdbc.update("""
                        INSERT INTO order_item_batches(order_item_id, batch_id, qty)
                        VALUES (?, ?, ?)
                        """, oi.getId(), t.batchId(), t.qtyTaken());

                jdbc.update("""
                        INSERT INTO inventory_movements
                          (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
                        VALUES
                          (?, ?, ?, 'sale', ?, now(), ?, 'order', ?)
                        """,
                        buffetId, product.getId(), t.batchId(),
                        -t.qtyTaken(),
                        user.getId(),
                        order.getId()
                );
            }
        }

        for (var cc : cartCombos) {
            int comboQty = cc.getQty() == null ? 1 : cc.getQty();
            BigDecimal comboPrice = nz(cc.getComboPriceSnapshot());

            Long orderComboId = jdbc.queryForObject("""
                    INSERT INTO order_combos(order_id, combo_template_id, qty, combo_price_snapshot, combo_name_snapshot)
                    VALUES (?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class,
                    order.getId(),
                    cc.getComboTemplate().getId(),
                    comboQty,
                    comboPrice,
                    cc.getComboTemplate().getName()
            );

            for (var it : cc.getItems()) {
                int oneComboItemQty = it.getQty() == null ? 1 : it.getQty();
                int totalItemQty = oneComboItemQty * comboQty;

                Long orderComboItemId = jdbc.queryForObject("""
                    INSERT INTO order_combo_items
                      (order_combo_id, combo_slot_id, product_id, qty, extra_price_snapshot, slot_name_snapshot, product_name_snapshot)
                    VALUES
                      (?, ?, ?, ?, ?, ?, ?)
                    RETURNING id
                    """, Long.class,
                        orderComboId,
                        it.getComboSlot().getId(),
                        it.getProduct().getId(),
                        totalItemQty,
                        it.getExtraPriceSnapshot(),
                        it.getComboSlot().getName(),
                        it.getProduct().getName()
                );

                List<BatchTake> takes = takeFromBatchesFefo(buffetId, it.getProduct().getId(), totalItemQty);
                for (var t : takes) touchedBatchIds.add(t.batchId());

                for (BatchTake t : takes) {
                    jdbc.update("""
                        INSERT INTO order_combo_item_batches(order_combo_item_id, batch_id, qty)
                        VALUES (?, ?, ?)
                        """, orderComboItemId, t.batchId(), t.qtyTaken());
                }

                int updated = jdbc.update("""
                        UPDATE inventory_items
                        SET quantity = quantity - ?,
                            updated_at = now()
                        WHERE buffet_id = ? AND product_id = ? AND quantity >= ?
                        """, totalItemQty, buffetId, it.getProduct().getId(), totalItemQty);

                if (updated == 0) {
                    throw new IllegalStateException("Нельзя списать комбо: общий остаток меньше списания или inventory_items нет");
                }

                for (BatchTake t : takes) {
                    jdbc.update("""
                            INSERT INTO inventory_movements
                              (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
                            VALUES
                              (?, ?, ?, 'sale', ?, now(), ?, 'order', ?)
                            """,
                            buffetId, it.getProduct().getId(), t.batchId(),
                            -t.qtyTaken(),
                            user.getId(),
                            order.getId()
                    );
                }
            }

            BigDecimal lineBase = comboPrice.multiply(BigDecimal.valueOf(comboQty));
            totalBase = totalBase.add(lineBase);
        }

        disableBatchDiscountsIfClosed(touchedBatchIds);

        order.setTotalAmount(scale2(totalBase));
        order.setDiscountAmount(scale2(totalDiscount.max(BigDecimal.ZERO)));
        order.setFinalAmount(scale2(totalBase.subtract(totalDiscount).max(BigDecimal.ZERO)));
        orderRepository.save(order);

        Payment p = new Payment();
        p.setOrder(order);
        p.setProvider("stub");
        p.setStatus("succeeded");
        p.setAmount(order.getFinalAmount());
        p.setCurrency("RUB");
        p.setPaidAt(LocalDateTime.now());
        p.setProviderPaymentId("stub-order-" + order.getId());
        paymentRepository.save(p);

        cartService.clear(user.getId(), buffetId);
        cartComboService.clearCombos(user.getId(), buffetId);

        return order;
    }

    private record BatchRow(long id, int qtyAvailable, String status) {}
    public record BatchTake(long batchId, int qtyTaken) {}

    private record PricingAgg(
            BigDecimal finalTotal,
            BigDecimal promoDiscountTotal,
            BigDecimal monthlyDiscountTotal,
            Map<Long, BigDecimal> batchDiscountByBatchId
    ) {}

    private PricingAgg priceByTakes(BigDecimal unitBase,
                                    BigDecimal promoPercent,
                                    BigDecimal monthlyPercent,
                                    List<BatchTake> takes) {

        Map<Long, BigDecimal> batchPct = loadBatchPercents(takes);

        BigDecimal finalTotal = BigDecimal.ZERO;
        BigDecimal promoDisc = BigDecimal.ZERO;
        BigDecimal monthlyDisc = BigDecimal.ZERO;
        Map<Long, BigDecimal> batchDiscByBatch = new HashMap<>();

        for (BatchTake t : takes) {
            BigDecimal qty = BigDecimal.valueOf(t.qtyTaken());

            BigDecimal batchPercent = batchPct.get(t.batchId());

            BigDecimal afterBatch = buyerPricingService.applyDiscount(unitBase, batchPercent);
            BigDecimal batchDiscount = unitBase.subtract(afterBatch).multiply(qty);

            BigDecimal afterPromo = buyerPricingService.applyDiscount(afterBatch, promoPercent);
            BigDecimal promoDiscount = afterBatch.subtract(afterPromo).multiply(qty);

            BigDecimal afterMonthly = buyerPricingService.applyDiscount(afterPromo, monthlyPercent);
            BigDecimal monthlyDiscount = afterPromo.subtract(afterMonthly).multiply(qty);

            finalTotal = finalTotal.add(afterMonthly.multiply(qty));

            promoDisc = promoDisc.add(promoDiscount);
            monthlyDisc = monthlyDisc.add(monthlyDiscount);

            if (batchDiscount.signum() > 0) {
                batchDiscByBatch.merge(t.batchId(), batchDiscount, BigDecimal::add);
            }
        }

        return new PricingAgg(
                scale2(finalTotal),
                scale2(promoDisc),
                scale2(monthlyDisc),
                batchDiscByBatch
        );
    }

    private Map<Long, BigDecimal> loadBatchPercents(List<BatchTake> takes) {
        if (takes == null || takes.isEmpty()) return Map.of();

        List<Long> ids = takes.stream().map(BatchTake::batchId).distinct().toList();
        String inSql = String.join(",", ids.stream().map(x -> "?").toList());

        String sql = """
            SELECT bd.batch_id, bd.percent
            FROM batch_discounts bd
            WHERE bd.is_active = TRUE
              AND bd.batch_id IN (%s)
            """.formatted(inSql);

        Map<Long, BigDecimal> map = new HashMap<>();

        Object[] args = ids.toArray();
        jdbc.query(sql, args, rs -> {
            map.put(rs.getLong("batch_id"), rs.getBigDecimal("percent"));
        });

        return map;
    }

    private void saveDiscountBreakdown(OrderItem oi, Long productId, PricingAgg agg) {
        for (var e : agg.batchDiscountByBatchId().entrySet()) {
            BigDecimal amount = nz(e.getValue());
            if (amount.signum() > 0) {
                OrderItemDiscount d = new OrderItemDiscount();
                d.setOrderItem(oi);
                d.setSourceType("batch");
                d.setSourceId(e.getKey());
                d.setAmount(scale2(amount));
                orderItemDiscountRepository.save(d);
            }
        }

        if (agg.promoDiscountTotal().signum() > 0) {
            OrderItemDiscount d = new OrderItemDiscount();
            d.setOrderItem(oi);
            d.setSourceType("product");
            d.setSourceId(productId);
            d.setAmount(scale2(agg.promoDiscountTotal()));
            orderItemDiscountRepository.save(d);
        }

        if (agg.monthlyDiscountTotal().signum() > 0) {
            OrderItemDiscount d = new OrderItemDiscount();
            d.setOrderItem(oi);
            d.setSourceType("personal");
            d.setSourceId(productId);
            d.setAmount(scale2(agg.monthlyDiscountTotal()));
            orderItemDiscountRepository.save(d);
        }
    }

    private List<BatchTake> takeFromBatchesFefo(Long buffetId, Long productId, int needQty) {
        List<BatchRow> rows = jdbc.query("""
                SELECT id, qty_available, status
                FROM inventory_batches
                WHERE buffet_id = ?
                  AND product_id = ?
                  AND status = 'active'
                  AND qty_available > 0
                ORDER BY (expires_at IS NULL), expires_at, received_at, id
                FOR UPDATE
                """,
                (rs, rn) -> new BatchRow(
                        rs.getLong("id"),
                        rs.getInt("qty_available"),
                        rs.getString("status")
                ),
                buffetId, productId
        );

        int remaining = needQty;
        List<BatchTake> takes = new ArrayList<>();

        for (BatchRow br : rows) {
            if (remaining <= 0) break;

            int take = Math.min(br.qtyAvailable(), remaining);

            int upd = jdbc.update("""
                    UPDATE inventory_batches
                    SET qty_available = qty_available - ?,
                        status = CASE WHEN (qty_available - ?) <= 0 THEN 'closed' ELSE status END
                    WHERE id = ?
                      AND qty_available >= ?
                    """, take, take, br.id(), take);

            if (upd != 1) {
                throw new IllegalStateException("Не удалось списать из партии id=" + br.id());
            }

            takes.add(new BatchTake(br.id(), take));
            remaining -= take;
        }

        if (remaining > 0) {
            throw new IllegalStateException("Недостаточно по партиям для списания. Нужно: " + needQty);
        }
        return takes;
    }

    private void disableBatchDiscountsIfClosed(Set<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) return;

        for (Long batchId : batchIds) {
            jdbc.update("""
                    UPDATE batch_discounts
                    SET is_active = FALSE
                    WHERE batch_id = ?
                      AND is_active = TRUE
                      AND EXISTS (
                          SELECT 1 FROM inventory_batches b
                          WHERE b.id = ? AND b.status = 'closed'
                      )
                    """, batchId, batchId);
        }
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    private static BigDecimal scale2(BigDecimal v) {
        return nz(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String generatePickupCode() {
        int x = ThreadLocalRandom.current().nextInt(1000, 10000);
        return String.valueOf(x);
    }
}
package com.example.Vkus.service;

import com.example.Vkus.entity.Order;
import com.example.Vkus.repository.InventoryItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import com.example.Vkus.security.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class SellerOrderService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InventoryItemRepository inventoryItemRepository; // оставил, хотя в cancel не нужен
    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbc;

    public SellerOrderService(OrderRepository orderRepository,
                              PaymentRepository paymentRepository,
                              InventoryItemRepository inventoryItemRepository,
                              CurrentUserService currentUserService,
                              JdbcTemplate jdbc) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.currentUserService = currentUserService;
        this.jdbc = jdbc;
    }

    @Transactional
    public void toAssembling(Long orderId) {
        Order o = loadSellerOrderOrThrow(orderId);
        if (isCancelled(o) || isIssued(o)) return;

        if (isCreated(o) || isAccepted(o)) {
            o.setStatus("assembling");
            orderRepository.save(o);
        }
    }

    @Transactional
    public void toReady(Long orderId) {
        Order o = loadSellerOrderOrThrow(orderId);
        if (isCancelled(o) || isIssued(o)) return;

        if (isAssembling(o) || isAccepted(o) || isCreated(o)) {
            o.setStatus("ready");
            orderRepository.save(o);
        }
    }

    @Transactional
    public void issueByPickupCode(Long orderId, String code) {
        Order o = loadSellerOrderOrThrow(orderId);

        if (isCancelled(o) || isIssued(o)) return;
        if (!isReady(o)) throw new IllegalStateException("Заказ ещё не готов к выдаче");

        if (o.getPickupCodeExpiresAt() != null && o.getPickupCodeExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Код выдачи просрочен");
        }

        String expected = o.getPickupCode() == null ? "" : o.getPickupCode().trim();
        String provided = code == null ? "" : code.trim();
        if (!expected.equals(provided)) throw new IllegalArgumentException("Неверный код выдачи");

        o.setStatus("issued");
        o.setIssuedAt(LocalDateTime.now());
        o.setSeller(currentUserService.getCurrentUser());
        orderRepository.save(o);

        // заглушка оплаты
        paymentRepository.findTopByOrderIdOrderByIdDesc(o.getId()).ifPresent(p -> {
            if ("pending".equalsIgnoreCase(p.getStatus())) {
                p.setStatus("succeeded");
                p.setPaidAt(LocalDateTime.now());
                paymentRepository.save(p);
            }
        });
    }

    @Transactional
    public void cancel(Long orderId) {
        Order o = loadSellerOrderOrThrow(orderId);

        if (isIssued(o) || isCancelled(o)) return;

        Long buffetId = o.getBuffet().getId();
        Long actorUserId = currentUserService.getCurrentUser().getId();

        // 1) статус
        o.setStatus("cancelled");
        orderRepository.save(o);

        // 2) "возврат денег" заглушка
        paymentRepository.findTopByOrderIdOrderByIdDesc(o.getId()).ifPresent(p -> {
            p.setStatus("cancelled");
            paymentRepository.save(p);
        });

        // ======================
        // 3) Возврат ОБЫЧНЫХ товаров: по order_item_batches
        // ======================
        List<BatchBackRow> backs = jdbc.query("""
                SELECT
                    oi.product_id      AS product_id,
                    oib.batch_id       AS batch_id,
                    oib.qty            AS qty
                FROM order_item_batches oib
                JOIN order_items oi ON oi.id = oib.order_item_id
                WHERE oi.order_id = ?
                FOR UPDATE
                """,
                (rs, rn) -> new BatchBackRow(
                        rs.getLong("product_id"),
                        rs.getLong("batch_id"),
                        rs.getInt("qty")
                ),
                o.getId()
        );

        for (BatchBackRow b : backs) {
            // вернуть в batch и открыть, если был closed
            jdbc.update("""
                    UPDATE inventory_batches
                    SET qty_available = qty_available + ?,
                        status = 'active'
                    WHERE id = ?
                    """, b.qty(), b.batchId());

            // вернуть общий остаток inventory_items (upsert)
            upsertInventoryPlus(buffetId, b.productId(), b.qty());

            // movement на возврат
            jdbc.update("""
                    INSERT INTO inventory_movements
                      (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
                    VALUES
                      (?, ?, ?, 'adjustment', ?, now(), ?, 'order', ?)
                    """,
                    buffetId, b.productId(), b.batchId(),
                    b.qty(),
                    actorUserId,
                    o.getId()
            );
        }

        // ======================
        // 4) Возврат КОМБО: по order_combo_item_batches (ИДЕАЛЬНО)
        // ======================
        List<BatchBackRow> comboBacks = jdbc.query("""
                SELECT
                    oci.product_id AS product_id,
                    ocib.batch_id  AS batch_id,
                    ocib.qty       AS qty
                FROM order_combos oc
                JOIN order_combo_items oci ON oci.order_combo_id = oc.id
                JOIN order_combo_item_batches ocib ON ocib.order_combo_item_id = oci.id
                WHERE oc.order_id = ?
                FOR UPDATE
                """,
                (rs, rn) -> new BatchBackRow(
                        rs.getLong("product_id"),
                        rs.getLong("batch_id"),
                        rs.getInt("qty")
                ),
                o.getId()
        );

        for (BatchBackRow b : comboBacks) {
            jdbc.update("""
                    UPDATE inventory_batches
                    SET qty_available = qty_available + ?,
                        status = 'active'
                    WHERE id = ?
                    """, b.qty(), b.batchId());

            upsertInventoryPlus(buffetId, b.productId(), b.qty());

            jdbc.update("""
                    INSERT INTO inventory_movements
                      (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
                    VALUES
                      (?, ?, ?, 'adjustment', ?, now(), ?, 'order', ?)
                    """,
                    buffetId, b.productId(), b.batchId(),
                    b.qty(),
                    actorUserId,
                    o.getId()
            );
        }
    }

    private record BatchBackRow(long productId, long batchId, int qty) {}

    private Order loadSellerOrderOrThrow(Long orderId) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        Order o = orderRepository.findById(orderId).orElseThrow();

        if (!o.getBuffet().getId().equals(buffetId)) {
            throw new IllegalStateException("Нет доступа к заказу другого буфета");
        }
        return o;
    }

    private void upsertInventoryPlus(Long buffetId, Long productId, int qtyPlus) {
        int upd = jdbc.update("""
                UPDATE inventory_items
                SET quantity = quantity + ?,
                    updated_at = now()
                WHERE buffet_id = ? AND product_id = ?
                """, qtyPlus, buffetId, productId);

        if (upd == 0) {
            jdbc.update("""
                    INSERT INTO inventory_items(buffet_id, product_id, quantity, updated_at)
                    VALUES (?, ?, ?, now())
                    """, buffetId, productId, qtyPlus);
        }
    }

    private boolean isCreated(Order o) { return "created".equalsIgnoreCase(o.getStatus()); }
    private boolean isAccepted(Order o) { return "accepted".equalsIgnoreCase(o.getStatus()); }
    private boolean isAssembling(Order o) { return "assembling".equalsIgnoreCase(o.getStatus()); }
    private boolean isReady(Order o) { return "ready".equalsIgnoreCase(o.getStatus()); }
    private boolean isIssued(Order o) { return "issued".equalsIgnoreCase(o.getStatus()); }
    private boolean isCancelled(Order o) { return "cancelled".equalsIgnoreCase(o.getStatus()); }
}
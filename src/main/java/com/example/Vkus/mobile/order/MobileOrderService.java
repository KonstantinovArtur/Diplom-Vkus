package com.example.Vkus.mobile.order;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.Payment;
import com.example.Vkus.mobile.order.dto.*;
import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import com.example.Vkus.security.NotEnoughStockException;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.CheckoutService;
import com.example.Vkus.service.StubPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MobileOrderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CheckoutService checkoutService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final StubPaymentService stubPaymentService;
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;

    public MobileOrderService(
            CheckoutService checkoutService,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentRepository paymentRepository,
            StubPaymentService stubPaymentService,
            JdbcTemplate jdbc,
            AuditLogService audit
    ) {
        this.checkoutService = checkoutService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.stubPaymentService = stubPaymentService;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @Transactional
    public MobileCheckoutResponse checkout(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        try {
            Order created = checkoutService.checkout();

            audit.log("MOBILE_CHECKOUT_SUCCESS", "order", created.getId(), Map.of(
                    "actorUserId", userId,
                    "buffetId", buffetId
            ));

            return new MobileCheckoutResponse(
                    true,
                    "Заказ успешно оформлен",
                    created.getId()
            );
        } catch (NotEnoughStockException ex) {
            audit.log("MOBILE_CHECKOUT_FAIL_NOT_ENOUGH_STOCK", "buffet", buffetId, Map.of(
                    "actorUserId", userId,
                    "buffetId", buffetId,
                    "error", ex.getMessage()
            ));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            audit.log("MOBILE_CHECKOUT_FAIL", "buffet", buffetId, Map.of(
                    "actorUserId", userId,
                    "buffetId", buffetId,
                    "error", ex.getMessage()
            ));
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<MobileOrderSummaryDto> getMyOrders(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        return orderRepository.findByUserIdAndBuffetIdOrderByCreatedAtDesc(userId, buffetId).stream()
                .map(order -> {
                    Payment payment = paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId()).orElse(null);
                    return new MobileOrderSummaryDto(
                            order.getId(),
                            order.getStatus(),
                            order.getCreatedAt() == null ? null : order.getCreatedAt().format(DT),
                            toDouble(order.getFinalAmount()),
                            order.getPickupCode(),
                            order.getPickupCodeExpiresAt() == null ? null : order.getPickupCodeExpiresAt().format(DT),
                            payment == null ? null : payment.getStatus()
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MobileOrderDetailResponse getOrderDetail(Jwt jwt, Long orderId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));

        if (!order.getUser().getId().equals(userId) || !order.getBuffet().getId().equals(buffetId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш заказ");
        }

        var items = orderItemRepository.findByOrderId(orderId).stream()
                .map(oi -> new MobileOrderItemDto(
                        oi.getId(),
                        oi.getProduct().getId(),
                        oi.getProduct().getName(),
                        oi.getQty(),
                        toDouble(oi.getUnitPriceSnapshot()),
                        toDouble(oi.getDiscountAmount()),
                        toDouble(oi.getFinalLineAmount())
                ))
                .toList();

        Payment payment = paymentRepository.findTopByOrderIdOrderByIdDesc(orderId).orElse(null);

        return new MobileOrderDetailResponse(
                order.getId(),
                order.getStatus(),
                order.getCreatedAt() == null ? null : order.getCreatedAt().format(DT),
                toDouble(order.getTotalAmount()),
                toDouble(order.getDiscountAmount()),
                toDouble(order.getFinalAmount()),
                order.getPickupCode(),
                order.getPickupCodeExpiresAt() == null ? null : order.getPickupCodeExpiresAt().format(DT),
                payment == null ? null : payment.getStatus(),
                payment == null ? null : payment.getProvider(),
                items,
                loadOrderCombos(orderId)
        );
    }

    @Transactional
    public MobilePayOrderResponse payOrder(Jwt jwt, Long orderId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));

        if (!order.getUser().getId().equals(userId) || !order.getBuffet().getId().equals(buffetId)) {
            audit.log("MOBILE_ORDER_PAY_FORBIDDEN", "order", orderId, Map.of(
                    "actorUserId", userId,
                    "buffetId", buffetId
            ));
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это не ваш заказ");
        }

        Payment payment = stubPaymentService.payOrderStub(orderId);

        audit.log("MOBILE_ORDER_PAY_STUB", "order", orderId, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId
        ));

        return new MobilePayOrderResponse(
                true,
                "Оплата прошла успешно",
                payment.getStatus()
        );
    }

    private List<MobileOrderComboDto> loadOrderCombos(Long orderId) {
        var heads = jdbc.query("""
                SELECT oc.id AS order_combo_id,
                       ct.name AS combo_name,
                       oc.qty AS qty,
                       oc.combo_price_snapshot AS price_snapshot
                FROM order_combos oc
                JOIN combo_templates ct ON ct.id = oc.combo_template_id
                WHERE oc.order_id = ?
                ORDER BY oc.id
                """,
                (rs, rn) -> new Object[]{
                        rs.getLong("order_combo_id"),
                        rs.getString("combo_name"),
                        rs.getInt("qty"),
                        rs.getBigDecimal("price_snapshot")
                },
                orderId
        );

        if (heads.isEmpty()) return List.of();

        LinkedHashMap<Long, MobileOrderComboDto> map = new LinkedHashMap<>();
        List<Long> comboIds = new ArrayList<>();

        for (Object[] r : heads) {
            Long ocId = (Long) r[0];
            String name = (String) r[1];
            Integer qty = (Integer) r[2];
            BigDecimal price = (BigDecimal) r[3];

            comboIds.add(ocId);
            map.put(ocId, new MobileOrderComboDto(
                    ocId,
                    name,
                    qty,
                    toDouble(price),
                    new ArrayList<>()
            ));
        }

        String inSql = String.join(",", comboIds.stream().map(x -> "?").toList());
        Object[] args = comboIds.toArray();

        String sql = """
                SELECT oci.order_combo_id AS order_combo_id,
                       cs.name AS slot_name,
                       p.name AS product_name,
                       oci.qty AS qty,
                       oci.extra_price_snapshot AS extra_price
                FROM order_combo_items oci
                JOIN combo_slots cs ON cs.id = oci.combo_slot_id
                JOIN products p ON p.id = oci.product_id
                WHERE oci.order_combo_id IN (%s)
                ORDER BY oci.order_combo_id, cs.sort_order, cs.id, oci.id
                """.formatted(inSql);

        jdbc.query(sql, rs -> {
            Long ocId = rs.getLong("order_combo_id");
            MobileOrderComboDto dto = map.get(ocId);
            if (dto == null) return;

            @SuppressWarnings("unchecked")
            List<MobileOrderComboItemDto> items = (List<MobileOrderComboItemDto>) dto.items();

            items.add(new MobileOrderComboItemDto(
                    rs.getString("slot_name"),
                    rs.getString("product_name"),
                    rs.getInt("qty"),
                    toDouble(rs.getBigDecimal("extra_price"))
            ));
        }, args);

        return new ArrayList<>(map.values());
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }

    private Long extractOptionalLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);
        return null;
    }
}
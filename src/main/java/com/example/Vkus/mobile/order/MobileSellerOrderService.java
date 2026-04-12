package com.example.Vkus.mobile.order;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.Payment;
import com.example.Vkus.mobile.order.dto.MobileOrderComboDto;
import com.example.Vkus.mobile.order.dto.MobileOrderComboItemDto;
import com.example.Vkus.mobile.order.dto.MobileOrderItemDto;
import com.example.Vkus.mobile.order.dto.MobileSellerIssueOrderRequest;
import com.example.Vkus.mobile.order.dto.MobileSellerOrderActionResponse;
import com.example.Vkus.mobile.order.dto.MobileSellerOrderDetailResponse;
import com.example.Vkus.mobile.order.dto.MobileSellerOrderSummaryDto;
import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.SellerOrderService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MobileSellerOrderService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId STORAGE_ZONE = ZoneId.of("UTC");
    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final SellerOrderService sellerOrderService;
    private final AuditLogService audit;
    private final JdbcTemplate jdbc;

    public MobileSellerOrderService(OrderRepository orderRepository,
                                    OrderItemRepository orderItemRepository,
                                    PaymentRepository paymentRepository,
                                    SellerOrderService sellerOrderService,
                                    AuditLogService audit,
                                    JdbcTemplate jdbc) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.sellerOrderService = sellerOrderService;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<MobileSellerOrderSummaryDto> getOrders(Jwt jwt) {
        Long actorUserId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);

        return orderRepository.findByBuffetIdOrderByCreatedAtDesc(buffetId).stream()
                .map(order -> {
                    Payment payment = paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId()).orElse(null);

                    return new MobileSellerOrderSummaryDto(
                            order.getId(),
                            order.getUser().getFullName(),
                            order.getStatus(),
                            formatMoscow(order.getCreatedAt()),
                            toDouble(order.getFinalAmount()),
                            payment == null ? null : payment.getStatus(),
                            canToAssembling(order),
                            canToReady(order),
                            canIssue(order),
                            canCancel(order)
                    );
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public MobileSellerOrderDetailResponse getOrderDetail(Jwt jwt, Long orderId) {
        extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);

        Order order = loadOrderForSeller(buffetId, orderId);

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

        return new MobileSellerOrderDetailResponse(
                order.getId(),
                order.getUser().getFullName(),
                order.getUser().getEmail(),
                order.getStatus(),
                formatMoscow(order.getCreatedAt()),
                toDouble(order.getTotalAmount()),
                toDouble(order.getDiscountAmount()),
                toDouble(order.getFinalAmount()),
                formatMoscow(order.getPickupCodeExpiresAt()),
                payment == null ? null : payment.getStatus(),
                payment == null ? null : payment.getProvider(),
                items,
                loadOrderCombos(orderId),
                canToAssembling(order),
                canToReady(order),
                canIssue(order),
                canCancel(order)
        );
    }

    @Transactional
    public MobileSellerOrderActionResponse toAssembling(Jwt jwt, Long orderId) {
        Long actorUserId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);
        loadOrderForSeller(buffetId, orderId);

        try {
            sellerOrderService.toAssembling(orderId);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        Order after = loadOrderForSeller(buffetId, orderId);

        audit.logAs(actorUserId, "MOBILE_SELLER_ORDER_TO_ASSEMBLING", "order", orderId, Map.of(
                "buffetId", buffetId,
                "newStatus", after.getStatus()
        ));

        return new MobileSellerOrderActionResponse(
                true,
                "Заказ переведён в сборку",
                after.getId(),
                after.getStatus()
        );
    }

    @Transactional
    public MobileSellerOrderActionResponse toReady(Jwt jwt, Long orderId) {
        Long actorUserId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);
        loadOrderForSeller(buffetId, orderId);

        try {
            sellerOrderService.toReady(orderId);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        Order after = loadOrderForSeller(buffetId, orderId);

        audit.logAs(actorUserId, "MOBILE_SELLER_ORDER_TO_READY", "order", orderId, Map.of(
                "buffetId", buffetId,
                "newStatus", after.getStatus()
        ));

        return new MobileSellerOrderActionResponse(
                true,
                "Заказ переведён в статус готов",
                after.getId(),
                after.getStatus()
        );
    }

    @Transactional
    public MobileSellerOrderActionResponse issue(Jwt jwt, Long orderId, MobileSellerIssueOrderRequest request) {
        Long actorUserId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);
        loadOrderForSeller(buffetId, orderId);

        String pickupCode = request == null ? null : request.pickupCode();
        if (pickupCode == null || pickupCode.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Введите код выдачи");
        }

        try {
            sellerOrderService.issueByPickupCode(orderId, pickupCode);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        Order after = loadOrderForSeller(buffetId, orderId);

        audit.logAs(actorUserId, "MOBILE_SELLER_ORDER_ISSUE", "order", orderId, Map.of(
                "buffetId", buffetId,
                "pickupCodeMasked", maskCode(pickupCode),
                "newStatus", after.getStatus()
        ));

        return new MobileSellerOrderActionResponse(
                true,
                "Заказ выдан",
                after.getId(),
                after.getStatus()
        );
    }

    @Transactional
    public MobileSellerOrderActionResponse cancel(Jwt jwt, Long orderId) {
        Long actorUserId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractRequiredBuffetId(jwt);
        ensureSellerAccess(jwt);
        loadOrderForSeller(buffetId, orderId);

        try {
            sellerOrderService.cancel(orderId);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        Order after = loadOrderForSeller(buffetId, orderId);

        audit.logAs(actorUserId, "MOBILE_SELLER_ORDER_CANCEL", "order", orderId, Map.of(
                "buffetId", buffetId,
                "newStatus", after.getStatus()
        ));

        return new MobileSellerOrderActionResponse(
                true,
                "Заказ отменён",
                after.getId(),
                after.getStatus()
        );
    }

    private Order loadOrderForSeller(Long buffetId, Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заказ не найден"));

        if (!order.getBuffet().getId().equals(buffetId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к заказу другого буфета");
        }

        return order;
    }

    private void ensureSellerAccess(Jwt jwt) {
        List<String> roles = extractRoles(jwt.getClaims().get("roles"));
        boolean allowed = roles.stream().anyMatch(r ->
                "seller".equalsIgnoreCase(r) || "buffet_admin".equalsIgnoreCase(r)
        );

        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нет доступа к заказам продавца");
        }
    }

    private List<String> extractRoles(Object value) {
        if (value instanceof Collection<?> collection) {
            return collection.stream()
                    .map(String::valueOf)
                    .toList();
        }
        return List.of();
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

    private boolean canToAssembling(Order order) {
        return "created".equalsIgnoreCase(order.getStatus())
                || "accepted".equalsIgnoreCase(order.getStatus());
    }

    private boolean canToReady(Order order) {
        return "created".equalsIgnoreCase(order.getStatus())
                || "accepted".equalsIgnoreCase(order.getStatus())
                || "assembling".equalsIgnoreCase(order.getStatus());
    }

    private boolean canIssue(Order order) {
        return "ready".equalsIgnoreCase(order.getStatus());
    }

    private boolean canCancel(Order order) {
        return !"issued".equalsIgnoreCase(order.getStatus())
                && !"cancelled".equalsIgnoreCase(order.getStatus());
    }

    private String maskCode(String code) {
        if (code == null) return null;
        String c = code.trim();
        if (c.length() <= 2) return "**";
        if (c.length() <= 4) return c.charAt(0) + "**" + c.charAt(c.length() - 1);
        return c.substring(0, 2) + "***" + c.substring(c.length() - 2);
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    private String formatMoscow(LocalDateTime value) {
        if (value == null) return null;
        return value.atZone(STORAGE_ZONE)
                .withZoneSameInstant(MOSCOW_ZONE)
                .format(DT);
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }

    private Long extractRequiredBuffetId(Jwt jwt) {
        Object value = jwt.getClaims().get("defaultBuffetId");
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
    }
}
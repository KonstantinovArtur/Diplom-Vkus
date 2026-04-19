package com.example.Vkus.service;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.OrderItem;
import com.example.Vkus.entity.Payment;
import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class OrderReceiptService {

    private static final DateTimeFormatter RECEIPT_NO_DT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final JdbcTemplate jdbc;

    public OrderReceiptService(OrderRepository orderRepository,
                               OrderItemRepository orderItemRepository,
                               PaymentRepository paymentRepository,
                               JdbcTemplate jdbc) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.jdbc = jdbc;
    }

    public ReceiptDto getBuyerReceipt(Long orderId, Long userId, Long buffetId) {
        Order order = orderRepository.findById(orderId).orElseThrow();

        if (!order.getUser().getId().equals(userId) || !order.getBuffet().getId().equals(buffetId)) {
            throw new IllegalStateException("Нет доступа к чеку этого заказа");
        }

        return buildReceipt(order);
    }

    private ReceiptDto buildReceipt(Order order) {
        Payment payment = paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId()).orElse(null);

        List<ReceiptItemDto> items = orderItemRepository.findByOrderId(order.getId()).stream()
                .map(this::mapItem)
                .toList();

        return new ReceiptDto(
                order.getId(),
                buildReceiptNumber(order),
                order.getCreatedAt(),
                payment == null ? null : payment.getPaidAt(),
                firstNonBlank(order.getBuyerNameSnapshot(), order.getUser().getFullName()),
                firstNonBlank(order.getBuyerEmailSnapshot(), order.getUser().getEmail()),
                firstNonBlank(order.getBuffetNameSnapshot(), order.getBuffet().getName()),
                order.getPickupCode(),
                nz(order.getTotalAmount()),
                nz(order.getDiscountAmount()),
                nz(order.getFinalAmount()),
                payment == null ? null : payment.getStatus(),
                payment == null ? null : payment.getProvider(),
                items,
                loadOrderCombos(order.getId())
        );
    }

    private ReceiptItemDto mapItem(OrderItem item) {
        return new ReceiptItemDto(
                firstNonBlank(item.getProductNameSnapshot(), item.getProduct().getName()),
                item.getQty(),
                nz(item.getUnitPriceSnapshot()),
                nz(item.getDiscountAmount()),
                nz(item.getFinalLineAmount())
        );
    }

    private List<ReceiptComboDto> loadOrderCombos(Long orderId) {
        var heads = jdbc.query("""
                SELECT oc.id AS order_combo_id,
                       COALESCE(oc.combo_name_snapshot, ct.name) AS combo_name,
                       oc.qty AS qty,
                       oc.combo_price_snapshot AS price_snapshot
                FROM order_combos oc
                LEFT JOIN combo_templates ct ON ct.id = oc.combo_template_id
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

        LinkedHashMap<Long, ReceiptComboDto> map = new LinkedHashMap<>();
        List<Long> comboIds = new ArrayList<>();

        for (Object[] row : heads) {
            Long comboId = (Long) row[0];
            comboIds.add(comboId);

            map.put(comboId, new ReceiptComboDto(
                    (String) row[1],
                    (Integer) row[2],
                    nz((BigDecimal) row[3]),
                    new ArrayList<>()
            ));
        }

        String inSql = String.join(",", comboIds.stream().map(id -> "?").toList());
        Object[] args = comboIds.toArray();

        String sql = """
                SELECT oci.order_combo_id AS order_combo_id,
                       COALESCE(oci.slot_name_snapshot, cs.name) AS slot_name,
                       COALESCE(oci.product_name_snapshot, p.name) AS product_name,
                       oci.qty AS qty,
                       oci.extra_price_snapshot AS extra_price
                FROM order_combo_items oci
                LEFT JOIN combo_slots cs ON cs.id = oci.combo_slot_id
                LEFT JOIN products p ON p.id = oci.product_id
                WHERE oci.order_combo_id IN (%s)
                ORDER BY oci.order_combo_id, oci.id
                """.formatted(inSql);

        jdbc.query(sql, rs -> {
            Long comboId = rs.getLong("order_combo_id");
            ReceiptComboDto dto = map.get(comboId);
            if (dto == null) return;

            dto.items().add(new ReceiptComboItemDto(
                    rs.getString("slot_name"),
                    rs.getString("product_name"),
                    rs.getInt("qty"),
                    rs.getBigDecimal("extra_price")
            ));
        }, args);

        return new ArrayList<>(map.values());
    }

    private String buildReceiptNumber(Order order) {
        LocalDateTime baseDate = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();
        return "RCPT-" + baseDate.format(RECEIPT_NO_DT) + "-" + order.getId();
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) return first;
        return second;
    }

    public record ReceiptDto(
            Long orderId,
            String receiptNumber,
            LocalDateTime createdAt,
            LocalDateTime paidAt,
            String buyerName,
            String buyerEmail,
            String buffetName,
            String pickupCode,
            BigDecimal totalAmount,
            BigDecimal discountAmount,
            BigDecimal finalAmount,
            String paymentStatus,
            String paymentProvider,
            List<ReceiptItemDto> items,
            List<ReceiptComboDto> combos
    ) {}

    public record ReceiptItemDto(
            String name,
            Integer qty,
            BigDecimal unitPrice,
            BigDecimal discountAmount,
            BigDecimal finalAmount
    ) {}

    public record ReceiptComboDto(
            String comboName,
            Integer qty,
            BigDecimal comboPriceSnapshot,
            List<ReceiptComboItemDto> items
    ) {}

    public record ReceiptComboItemDto(
            String slotName,
            String productName,
            Integer qty,
            BigDecimal extraPriceSnapshot
    ) {}
}
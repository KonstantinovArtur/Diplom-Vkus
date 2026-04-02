package com.example.Vkus.web.seller;

import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.SellerOrderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

@Controller
@RequestMapping("/seller/orders")
public class SellerOrdersController {

    private final CurrentUserService currentUserService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final SellerOrderService sellerOrderService;
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;

    public SellerOrdersController(CurrentUserService currentUserService,
                                  OrderRepository orderRepository,
                                  OrderItemRepository orderItemRepository,
                                  PaymentRepository paymentRepository,
                                  SellerOrderService sellerOrderService,
                                  JdbcTemplate jdbc,
                                  AuditLogService audit) {
        this.currentUserService = currentUserService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.sellerOrderService = sellerOrderService;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        model.addAttribute("orders", orderRepository.findByBuffetIdOrderByCreatedAtDesc(buffetId));
        return "seller/orders/list";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id, Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        var order = orderRepository.findById(id).orElseThrow();
        if (!order.getBuffet().getId().equals(buffetId)) return "redirect:/seller/orders";

        model.addAttribute("order", order);
        model.addAttribute("items", orderItemRepository.findByOrderId(order.getId()));
        model.addAttribute("payment", paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId()).orElse(null));

        // ✅ добавили комбо
        model.addAttribute("combos", loadOrderCombos(order.getId()));

        return "seller/orders/details";
    }

    @PostMapping("/{id}/assembling")
    public String toAssembling(@PathVariable Long id) {
        Long actorId = currentUserService.getCurrentUser().getId();
        var before = orderRepository.findById(id).orElse(null);

        sellerOrderService.toAssembling(id);

        var after = orderRepository.findById(id).orElse(null);
        audit.log("ORDER_TO_ASSEMBLING", "order", id, Map.of(
                "before", snapshotOrder(before),
                "after", snapshotOrder(after),
                "actorUserId", actorId
        ));

        return "redirect:/seller/orders/" + id;
    }

    @PostMapping("/{id}/ready")
    public String toReady(@PathVariable Long id) {
        Long actorId = currentUserService.getCurrentUser().getId();
        var before = orderRepository.findById(id).orElse(null);

        sellerOrderService.toReady(id);

        var after = orderRepository.findById(id).orElse(null);
        audit.log("ORDER_TO_READY", "order", id, Map.of(
                "before", snapshotOrder(before),
                "after", snapshotOrder(after),
                "actorUserId", actorId
        ));

        return "redirect:/seller/orders/" + id;
    }

    @PostMapping("/{id}/issue")
    public String issue(@PathVariable Long id,
                        @RequestParam String pickupCode,
                        RedirectAttributes ra) {

        Long actorId = currentUserService.getCurrentUser().getId();
        var before = orderRepository.findById(id).orElse(null);

        try {
            sellerOrderService.issueByPickupCode(id, pickupCode);

            var after = orderRepository.findById(id).orElse(null);
            audit.log("ORDER_ISSUE", "order", id, Map.of(
                    "before", snapshotOrder(before),
                    "after", snapshotOrder(after),
                    "pickupCodeMasked", maskCode(pickupCode),
                    "actorUserId", actorId
            ));
        } catch (Exception e) {
            audit.log("ORDER_ISSUE_FAIL", "order", id, Map.of(
                    "before", snapshotOrder(before),
                    "pickupCodeMasked", maskCode(pickupCode),
                    "error", e.getMessage(),
                    "actorUserId", actorId
            ));
            ra.addFlashAttribute("err", e.getMessage());
        }
        return "redirect:/seller/orders/" + id;
    }

    @PostMapping("/{id}/cancel")
    public String cancel(@PathVariable Long id, RedirectAttributes ra) {
        Long actorId = currentUserService.getCurrentUser().getId();
        var before = orderRepository.findById(id).orElse(null);

        sellerOrderService.cancel(id);

        var after = orderRepository.findById(id).orElse(null);
        audit.log("ORDER_CANCEL", "order", id, Map.of(
                "before", snapshotOrder(before),
                "after", snapshotOrder(after),
                "actorUserId", actorId
        ));

        ra.addFlashAttribute("msg", "Заказ отменён, деньги возвращены (заглушка), товар возвращён на склад.");
        return "redirect:/seller/orders/" + id;
    }

    private String maskCode(String code) {
        if (code == null) return null;
        String c = code.trim();
        if (c.length() <= 2) return "**";
        if (c.length() <= 4) return c.charAt(0) + "**" + c.charAt(c.length() - 1);
        return c.substring(0, 2) + "***" + c.substring(c.length() - 2);
    }

    private Map<String, Object> snapshotOrder(Object order) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (order == null) {
            m.put("exists", false);
            return m;
        }
        m.put("exists", true);

        // безопасно через reflection, чтобы не ловить ошибки геттеров
        m.put("id", safeGet(order, "getId"));
        m.put("status", safeGet(order, "getStatus"));
        m.put("createdAt", safeGet(order, "getCreatedAt"));
        m.put("total", safeGet(order, "getTotal"));
        m.put("pickupCode", safeGet(order, "getPickupCode")); // если есть
        m.put("buyerId", safeGet(order, "getBuyerId"));       // если есть
        return m;
    }

    private Object safeGet(Object obj, String methodName) {
        try {
            var m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    // =========================
    // Комбо в заказе (для продавца) — оставил как было
    // =========================
    public record SellerOrderComboVm(
            Long orderComboId,
            String comboName,
            Integer qty,
            BigDecimal comboPriceSnapshot,
            List<SellerOrderComboItemVm> items
    ) {}

    public record SellerOrderComboItemVm(
            String slotName,
            String productName,
            Integer qty,
            BigDecimal extraPriceSnapshot
    ) {}

    private List<SellerOrderComboVm> loadOrderCombos(Long orderId) {
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

        LinkedHashMap<Long, SellerOrderComboVm> map = new LinkedHashMap<>();
        List<Long> comboIds = new ArrayList<>();

        for (Object[] r : heads) {
            Long ocId = (Long) r[0];
            String name = (String) r[1];
            Integer qty = (Integer) r[2];
            BigDecimal price = (BigDecimal) r[3];

            comboIds.add(ocId);
            map.put(ocId, new SellerOrderComboVm(ocId, name, qty, price, new ArrayList<>()));
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

        jdbc.query(sql,
                rs -> {
                    Long ocId = rs.getLong("order_combo_id");
                    SellerOrderComboVm vm = map.get(ocId);
                    if (vm == null) return;

                    vm.items().add(new SellerOrderComboItemVm(
                            rs.getString("slot_name"),
                            rs.getString("product_name"),
                            rs.getInt("qty"),
                            rs.getBigDecimal("extra_price")
                    ));
                },
                args
        );

        return new ArrayList<>(map.values());
    }
}
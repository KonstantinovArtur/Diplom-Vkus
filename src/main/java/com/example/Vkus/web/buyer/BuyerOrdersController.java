package com.example.Vkus.web.buyer;

import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.security.NotEnoughStockException;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.CartComboService;
import com.example.Vkus.service.CartService;
import com.example.Vkus.service.CheckoutService;
import com.example.Vkus.service.OrderReceiptService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/orders")
public class BuyerOrdersController {

    private static final ZoneId MOSCOW_ZONE = ZoneId.of("Europe/Moscow");
    private static final DateTimeFormatter VIEW_DT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final CheckoutService checkoutService;
    private final CurrentUserService currentUserService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;
    private final CartService cartService;
    private final CartComboService cartComboService;
    private final OrderReceiptService orderReceiptService;

    public BuyerOrdersController(CheckoutService checkoutService,
                                 CurrentUserService currentUserService,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 PaymentRepository paymentRepository,
                                 JdbcTemplate jdbc,
                                 AuditLogService audit,
                                 CartService cartService,
                                 CartComboService cartComboService,
                                 OrderReceiptService orderReceiptService) {
        this.checkoutService = checkoutService;
        this.currentUserService = currentUserService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.jdbc = jdbc;
        this.audit = audit;
        this.cartService = cartService;
        this.cartComboService = cartComboService;
        this.orderReceiptService = orderReceiptService;
    }

    @GetMapping("/checkout")
    public String checkoutPage(Model model, RedirectAttributes ra) {
        var user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        boolean hasItems = hasCheckoutCart(user.getId(), buffetId);
        if (!hasItems) {
            ra.addFlashAttribute("msg", "Корзина пуста или недоступна для текущего буфета.");
            return "redirect:/cart";
        }

        model.addAttribute("checkoutBuffetId", buffetId);
        return "buyer/checkout";
    }

    @PostMapping("/checkout")
    public String doCheckout(@RequestParam("checkoutBuffetId") Long checkoutBuffetId,
                             RedirectAttributes ra) {
        var user = currentUserService.getCurrentUser();
        Long currentBuffetId = currentUserService.getCurrentBuffetIdOrThrow();

        if (!Objects.equals(checkoutBuffetId, currentBuffetId)) {
            audit.log("CHECKOUT_ABORTED_BUFFET_CHANGED", "buffet", currentBuffetId, Map.of(
                    "actorUserId", user.getId(),
                    "checkoutBuffetId", checkoutBuffetId,
                    "currentBuffetId", currentBuffetId
            ));

            ra.addFlashAttribute("msg", "Активный буфет был изменён. Проверьте корзину перед оформлением заказа.");
            return "redirect:/cart";
        }

        if (!hasCheckoutCart(user.getId(), currentBuffetId)) {
            ra.addFlashAttribute("msg", "Корзина пуста или недоступна для текущего буфета.");
            return "redirect:/cart";
        }

        try {
            var created = checkoutService.checkout();

            audit.log("CHECKOUT_SUCCESS", "order", created.getId(), Map.of(
                    "actorUserId", user.getId(),
                    "buffetId", currentBuffetId
            ));

            return "redirect:/orders/" + created.getId();
        } catch (NotEnoughStockException ex) {
            audit.log("CHECKOUT_FAIL_NOT_ENOUGH_STOCK", "buffet", currentBuffetId, Map.of(
                    "actorUserId", user.getId(),
                    "buffetId", currentBuffetId,
                    "error", ex.getMessage()
            ));

            ra.addFlashAttribute("err", ex.getMessage());
            return "redirect:/cart";
        } catch (IllegalStateException ex) {
            audit.log("CHECKOUT_FAIL_INVALID_STATE", "buffet", currentBuffetId, Map.of(
                    "actorUserId", user.getId(),
                    "buffetId", currentBuffetId,
                    "error", ex.getMessage()
            ));

            ra.addFlashAttribute("msg", "Оформление заказа недоступно. Проверьте корзину и активный буфет.");
            return "redirect:/cart";
        }
    }

    @GetMapping("/{id}")
    public String orderDetails(@PathVariable Long id, Model model) {
        var user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        var order = orderRepository.findById(id).orElseThrow();
        if (!order.getUser().getId().equals(user.getId()) || !order.getBuffet().getId().equals(buffetId)) {
            return "redirect:/orders/my";
        }

        var payment = paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId()).orElse(null);

        model.addAttribute("order", order);
        model.addAttribute("items", orderItemRepository.findByOrderId(order.getId()));
        model.addAttribute("payment", payment);
        model.addAttribute("combos", loadOrderCombos(order.getId()));

        model.addAttribute("orderCreatedAtMsk", formatMsk(order.getCreatedAt()));
        model.addAttribute("pickupCodeExpiresAtMsk", formatMsk(order.getPickupCodeExpiresAt()));
        model.addAttribute("paymentPaidAtMsk", payment != null ? formatMskOrNull(payment.getPaidAt()) : null);

        return "buyer/order-success";
    }

    @GetMapping("/{id}/receipt")
    public String receipt(@PathVariable Long id, Model model, RedirectAttributes ra) {
        var user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        try {
            var receipt = orderReceiptService.getBuyerReceipt(id, user.getId(), buffetId);
            model.addAttribute("receipt", receipt);
            model.addAttribute("receiptCreatedAtMsk", formatMsk(receipt.createdAt()));
            model.addAttribute("receiptPaidAtMsk", formatMskOrNull(receipt.paidAt()));
            return "buyer/order-receipt";
        } catch (Exception ex) {
            ra.addFlashAttribute("err", "Не удалось открыть чек по этому заказу.");
            return "redirect:/orders/my";
        }
    }

    @GetMapping("/my")
    public String myOrders(Model model) {
        var user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        var orderRows = orderRepository.findByUserIdAndBuffetIdOrderByCreatedAtDesc(user.getId(), buffetId)
                .stream()
                .map(o -> new BuyerOrderListVm(
                        o.getId(),
                        o.getStatus(),
                        o.getFinalAmount(),
                        o.getCreatedAt() == null ? "—" : o.getCreatedAt().format(VIEW_DT)
                ))
                .toList();

        model.addAttribute("orders", orderRows);
        return "buyer/my-orders";
    }

    public record BuyerOrderListVm(
            Long id,
            String status,
            BigDecimal finalAmount,
            String createdAtMsk
    ) {}

    public record BuyerOrderComboVm(
            Long orderComboId,
            String comboName,
            Integer qty,
            BigDecimal comboPriceSnapshot,
            List<BuyerOrderComboItemVm> items
    ) {}

    public record BuyerOrderComboItemVm(
            String slotName,
            String productName,
            Integer qty,
            BigDecimal extraPriceSnapshot
    ) {}

    private boolean hasCheckoutCart(Long userId, Long buffetId) {
        return !cartService.getItems(userId, buffetId).isEmpty()
                || !cartComboService.getCombos(userId, buffetId).isEmpty();
    }

    private List<BuyerOrderComboVm> loadOrderCombos(Long orderId) {
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

        LinkedHashMap<Long, BuyerOrderComboVm> map = new LinkedHashMap<>();
        List<Long> comboIds = new ArrayList<>();

        for (Object[] r : heads) {
            Long ocId = (Long) r[0];
            String name = (String) r[1];
            Integer qty = (Integer) r[2];
            BigDecimal price = (BigDecimal) r[3];

            comboIds.add(ocId);
            map.put(ocId, new BuyerOrderComboVm(ocId, name, qty, price, new ArrayList<>()));
        }

        String inSql = String.join(",", comboIds.stream().map(x -> "?").toList());
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
                ORDER BY oci.order_combo_id, cs.sort_order, cs.id, oci.id
                """.formatted(inSql);

        jdbc.query(sql,
                rs -> {
                    Long ocId = rs.getLong("order_combo_id");
                    BuyerOrderComboVm vm = map.get(ocId);
                    if (vm == null) return;

                    vm.items().add(new BuyerOrderComboItemVm(
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

    private String formatMsk(LocalDateTime value) {
        if (value == null) {
            return "—";
        }

        return value.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(MOSCOW_ZONE)
                .format(VIEW_DT);
    }

    private String formatMskOrNull(LocalDateTime value) {
        if (value == null) {
            return null;
        }
        return formatMsk(value);
    }
}
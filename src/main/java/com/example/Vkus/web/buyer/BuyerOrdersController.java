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
import com.example.Vkus.service.StubPaymentService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.*;

@Controller
@RequestMapping("/orders")
public class BuyerOrdersController {

    private final CheckoutService checkoutService;
    private final CurrentUserService currentUserService;
    private final StubPaymentService stubPaymentService;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentRepository paymentRepository;
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;
    private final CartService cartService;
    private final CartComboService cartComboService;

    public BuyerOrdersController(CheckoutService checkoutService,
                                 CurrentUserService currentUserService,
                                 OrderRepository orderRepository,
                                 OrderItemRepository orderItemRepository,
                                 PaymentRepository paymentRepository,
                                 StubPaymentService stubPaymentService,
                                 JdbcTemplate jdbc,
                                 AuditLogService audit,
                                 CartService cartService,
                                 CartComboService cartComboService) {
        this.checkoutService = checkoutService;
        this.currentUserService = currentUserService;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentRepository = paymentRepository;
        this.stubPaymentService = stubPaymentService;
        this.jdbc = jdbc;
        this.audit = audit;
        this.cartService = cartService;
        this.cartComboService = cartComboService;
    }

    @PostMapping("/{id}/pay")
    public String pay(@PathVariable Long id) {
        var user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        var order = orderRepository.findById(id).orElseThrow();
        if (!order.getUser().getId().equals(user.getId()) || !order.getBuffet().getId().equals(buffetId)) {
            audit.log("ORDER_PAY_FORBIDDEN", "order", id, Map.of(
                    "actorUserId", user.getId(),
                    "buffetId", buffetId
            ));
            return "redirect:/orders/my";
        }

        stubPaymentService.payOrderStub(id);

        audit.log("ORDER_PAY_STUB", "order", id, Map.of(
                "actorUserId", user.getId(),
                "buffetId", buffetId
        ));

        return "redirect:/orders/" + id;
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

        model.addAttribute("order", order);
        model.addAttribute("items", orderItemRepository.findByOrderId(order.getId()));
        model.addAttribute("payment", paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId()).orElse(null));
        model.addAttribute("combos", loadOrderCombos(order.getId()));

        return "buyer/order-success";
    }

    @GetMapping("/my")
    public String myOrders(Model model) {
        var user = currentUserService.getCurrentUser();
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        model.addAttribute("orders",
                orderRepository.findByUserIdAndBuffetIdOrderByCreatedAtDesc(user.getId(), buffetId));
        return "buyer/my-orders";
    }

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
}
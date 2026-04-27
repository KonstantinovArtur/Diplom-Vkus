package com.example.Vkus.web.buyer;

import com.example.Vkus.entity.CartItem;
import com.example.Vkus.security.CurrentUserFacade;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.BuyerPricingService;
import com.example.Vkus.service.CartComboService;
import com.example.Vkus.service.CartService;
import com.example.Vkus.service.InventoryBatchConsumptionService;
import com.example.Vkus.web.dto.CartLineVm;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Controller
@RequestMapping("/cart")
public class BuyerCartController {

    private final CurrentUserFacade currentUser;
    private final CartService cartService;
    private final CartComboService cartComboService;
    private final BuyerPricingService pricingService;
    private final InventoryBatchConsumptionService batchService;
    private final AuditLogService audit;

    public BuyerCartController(CurrentUserFacade currentUser,
                               CartService cartService,
                               CartComboService cartComboService,
                               BuyerPricingService pricingService,
                               InventoryBatchConsumptionService batchService,
                               AuditLogService audit) {
        this.currentUser = currentUser;
        this.cartService = cartService;
        this.cartComboService = cartComboService;
        this.pricingService = pricingService;
        this.batchService = batchService;
        this.audit = audit;
    }

    @GetMapping
    public String cart(Model model) {
        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        List<CartItem> cartItems = cartService.getItems(userId, buffetId);

        var products = cartItems.stream().map(CartItem::getProduct).toList();
        Map<Long, BuyerPricingService.Discounts> discounts =
                pricingService.resolveDiscounts(userId, buffetId, products);

        // ✅ чтобы страница не падала — собираем ошибки по строкам
        List<String> cartErrors = new ArrayList<>();
        // можно использовать в шаблоне, чтобы помечать "нет в наличии"
        Set<Long> unavailableProductIds = new HashSet<>();

        List<CartLineVm> lines = cartItems.stream().map(ci -> {
            var p = ci.getProduct();
            int qty = ci.getQty();

            var d = discounts.get(p.getId());
            BigDecimal promo = d != null ? d.promoPercent() : null;
            BigDecimal monthly = d != null ? d.monthlyPercent() : null;

            BigDecimal base = p.getBasePrice();

            try {
                // 1) план списания по FEFO (как checkout, но без UPDATE)
                List<InventoryBatchConsumptionService.BatchTake> takes =
                        batchService.planConsumeFromBatches(buffetId, p.getId(), qty);

                // 2) подтягиваем batch-discount по использованным партиям
                List<Long> batchIds = takes.stream().map(t -> t.batchId()).distinct().toList();
                Map<Long, BigDecimal> batchPercents = batchService.loadActiveBatchDiscountPercents(batchIds);

                // 3) точная сумма по партиям
                BigDecimal lineTotal = BigDecimal.ZERO;

                BigDecimal maxBatch = null;
                boolean mixedOrPartial = false;

                for (var t : takes) {
                    BigDecimal batch = batchPercents.get(t.batchId()); // может быть null
                    if (batch != null) {
                        if (maxBatch == null || batch.compareTo(maxBatch) > 0) maxBatch = batch;
                    } else {
                        mixedOrPartial = true;
                    }

                    BigDecimal unit = pricingService.applyThreeDiscounts(base, batch, promo, monthly);
                    lineTotal = lineTotal.add(unit.multiply(BigDecimal.valueOf(t.qtyTaken())));
                }

                if (maxBatch != null && takes.size() > 1) {
                    BigDecimal first = batchPercents.get(takes.get(0).batchId());
                    for (var t : takes) {
                        BigDecimal b = batchPercents.get(t.batchId());
                        if (!Objects.equals(b, first)) {
                            mixedOrPartial = true;
                            break;
                        }
                    }
                }

                BigDecimal finalUnit = qty > 0
                        ? lineTotal.divide(BigDecimal.valueOf(qty), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                String batchText = null;
                BigDecimal batchForVm = null;

                if (maxBatch != null) {
                    batchForVm = maxBatch;
                    batchText = BuyerPricingService.asLabel(mixedOrPartial ? "Уценка до" : "Уценка", maxBatch);
                }

                String promoText = promo != null ? BuyerPricingService.asLabel("Акция", promo) : null;
                String monthlyText = monthly != null ? BuyerPricingService.asLabel("Скидка месяца", monthly) : null;

                return new CartLineVm(
                        p.getId(),
                        p.getName(),
                        p.getCategory() != null ? p.getCategory().getName() : "—",
                        p.getImageData() != null && p.getImageData().length > 0,
                        qty,
                        base,
                        finalUnit,

                        batchForVm,
                        batchText,

                        promo,
                        promoText,

                        monthly,
                        monthlyText,

                        lineTotal
                );

            } catch (IllegalStateException ex) {
                // ✅ ВОТ ТУТ ФИКС: не падаем, а отмечаем строку как недоступную
                unavailableProductIds.add(p.getId());
                cartErrors.add(p.getName() + ": " + ex.getMessage());

                // Строку рисуем, но сумма = 0, чтобы total не ломался
                String promoText = promo != null ? BuyerPricingService.asLabel("Акция", promo) : null;
                String monthlyText = monthly != null ? BuyerPricingService.asLabel("Скидка месяца", monthly) : null;

                return new CartLineVm(
                        p.getId(),
                        p.getName(),
                        p.getCategory() != null ? p.getCategory().getName() : "—",
                        p.getImageData() != null && p.getImageData().length > 0,
                        qty,
                        base,
                        BigDecimal.ZERO,

                        null,
                        null,

                        promo,
                        promoText,

                        monthly,
                        monthlyText,

                        BigDecimal.ZERO
                );
            }
        }).toList();

        BigDecimal totalItems = lines.stream()
                .map(CartLineVm::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        var combos = cartComboService.getCombos(userId, buffetId);

        BigDecimal totalCombos = combos.stream()
                .map(cc -> cc.getComboPriceSnapshot().multiply(BigDecimal.valueOf(cc.getQty())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("lines", lines);
        model.addAttribute("combos", combos);
        model.addAttribute("total", totalItems.add(totalCombos));

        // ✅ добавили в модель
        model.addAttribute("cartErrors", cartErrors);
        model.addAttribute("unavailableProductIds", unavailableProductIds);

        return "buyer/cart";
    }

    @PostMapping("/clear")
    public String clear() {
        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        cartService.clear(userId, buffetId);
        cartComboService.clearCombos(userId, buffetId);

        Map<String, Object> meta = new HashMap<>();
        meta.put("actorUserId", userId);
        meta.put("buffetId", buffetId);

        audit.log("CART_CLEAR", "cart", null, meta);

        return "redirect:/cart";
    }

    @PostMapping("/add")
    public String add(@RequestParam Long productId,
                      @RequestParam(defaultValue = "1") int qty,
                      @RequestHeader(value = "Referer", required = false) String referer,
                      RedirectAttributes ra) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        if (qty <= 0) qty = 1;

        // ✅ ВАЖНО: заранее проверяем доступность по партиям (и для случая "никогда не завозили")
        try {
            batchService.planConsumeFromBatches(buffetId, productId, qty);
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("cartError", ex.getMessage());
            return "redirect:" + (referer != null ? referer : "/menu");
        }

        cartService.add(userId, buffetId, productId, qty);

        audit.log("CART_ADD_PRODUCT", "product", productId, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId,
                "qty", qty,
                "referer", referer
        ));

        return "redirect:" + (referer != null ? referer : "/menu");
    }

    @PostMapping("/setQty")
    public String setQty(@RequestParam Long productId,
                         @RequestParam int qty,
                         RedirectAttributes ra) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        // ✅ если ставят qty больше чем доступно — лучше не падать
        try {
            if (qty > 0) {
                batchService.planConsumeFromBatches(buffetId, productId, qty);
            }
        } catch (IllegalStateException ex) {
            ra.addFlashAttribute("cartQtyError", ex.getMessage());
            return "redirect:/cart";
        }

        cartService.setQty(userId, buffetId, productId, qty);

        Map<String, Object> meta = new HashMap<>();
        meta.put("actorUserId", userId);
        meta.put("buffetId", buffetId);
        meta.put("qty", qty);

        audit.log("CART_SET_QTY", "product", productId, meta);

        return "redirect:/cart";
    }

    @PostMapping("/remove")
    public String remove(@RequestParam Long productId) {
        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        cartService.remove(userId, buffetId, productId);

        Map<String, Object> meta = new HashMap<>();
        meta.put("actorUserId", userId);
        meta.put("buffetId", buffetId);

        audit.log("CART_REMOVE_PRODUCT", "product", productId, meta);

        return "redirect:/cart";
    }

    @PostMapping("/remove-combo")
    public String removeCombo(@RequestParam Long cartComboId) {
        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        cartComboService.removeCombo(userId, buffetId, cartComboId);

        Map<String, Object> meta = new HashMap<>();
        meta.put("actorUserId", userId);
        meta.put("buffetId", buffetId);

        audit.log("CART_REMOVE_COMBO", "cart_combo", cartComboId, meta);

        return "redirect:/cart";
    }
    @PostMapping("/add-ajax")
    @ResponseBody
    public Map<String, Object> addAjax(@RequestParam Long productId,
                                       @RequestParam(defaultValue = "1") int qty) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        if (qty <= 0) {
            qty = 1;
        }

        try {
            batchService.planConsumeFromBatches(buffetId, productId, qty);
        } catch (IllegalStateException ex) {
            return Map.of(
                    "ok", false,
                    "message", ex.getMessage()
            );
        }

        cartService.add(userId, buffetId, productId, qty);

        int cartQty = cartService.getProductQty(userId, buffetId, productId);

        audit.log("CART_ADD_PRODUCT", "product", productId, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId,
                "qty", qty,
                "mode", "ajax"
        ));

        return Map.of(
                "ok", true,
                "productId", productId,
                "addedQty", qty,
                "cartQty", cartQty
        );
    }
    @PostMapping("/setQty-ajax")
    @ResponseBody
    public Map<String, Object> setQtyAjax(@RequestParam Long productId,
                                          @RequestParam int qty) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        if (qty < 0) {
            qty = 0;
        }

        try {
            if (qty > 0) {
                batchService.planConsumeFromBatches(buffetId, productId, qty);
            }

            cartService.setQty(userId, buffetId, productId, qty);

            int cartQty = qty > 0
                    ? cartService.getProductQty(userId, buffetId, productId)
                    : 0;

            audit.log("CART_SET_QTY", "product", productId, Map.of(
                    "actorUserId", userId,
                    "buffetId", buffetId,
                    "qty", qty,
                    "mode", "ajax"
            ));

            return Map.of(
                    "ok", true,
                    "productId", productId,
                    "cartQty", cartQty
            );

        } catch (IllegalStateException | IllegalArgumentException ex) {
            return Map.of(
                    "ok", false,
                    "message", ex.getMessage()
            );
        }
    }
}

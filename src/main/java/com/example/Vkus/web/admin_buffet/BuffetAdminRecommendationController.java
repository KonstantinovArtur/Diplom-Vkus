package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.BuffetAdminRecommendationService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin-buffet/recommendations")
public class BuffetAdminRecommendationController {

    private final BuffetAdminRecommendationService svc;
    private final AuditLogService audit;

    public BuffetAdminRecommendationController(BuffetAdminRecommendationService svc,
                                               AuditLogService audit) {
        this.svc = svc;
        this.audit = audit;
    }

    @GetMapping
    public String page(@RequestParam(value = "productId", required = false) Long productId,
                       Authentication auth,
                       Model model) {

        var ctx = svc.requireCtx(auth);
        var products = svc.productsInBuffet(ctx.buffetId());

        Long selectedProductId = productId;
        if (selectedProductId == null && !products.isEmpty()) {
            selectedProductId = products.get(0).getId();
        }

        model.addAttribute("products", products);
        model.addAttribute("selectedProductId", selectedProductId);

        if (selectedProductId != null) {
            model.addAttribute("recommendations", svc.list(ctx.buffetId(), selectedProductId));
            model.addAttribute("candidateProducts", svc.candidateProducts(ctx.buffetId(), selectedProductId));
        } else {
            model.addAttribute("recommendations", List.of());
            model.addAttribute("candidateProducts", List.of());
        }

        return "admin-buffet/recommendations/list";
    }

    @PostMapping("/add")
    public String add(@RequestParam("productId") Long productId,
                      @RequestParam("recommendedProductId") Long recommendedProductId,
                      @RequestParam(value = "sortOrder", required = false) Integer sortOrder,
                      Authentication auth,
                      RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.add(ctx.buffetId(), ctx.userId(), productId, recommendedProductId, sortOrder);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("buffetId", ctx.buffetId());
            meta.put("productId", productId);
            meta.put("recommendedProductId", recommendedProductId);
            meta.put("sortOrder", sortOrder);

            audit.log("PRODUCT_RECOMMENDATION_ADD", "product_recommendation", null, meta);
            ra.addFlashAttribute("ok", "Рекомендация добавлена");
        } catch (Exception e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/admin-buffet/recommendations?productId=" + productId;
    }

    @PostMapping("/{id}/sort")
    public String updateSort(@PathVariable Long id,
                             @RequestParam("productId") Long productId,
                             @RequestParam("sortOrder") Integer sortOrder,
                             Authentication auth,
                             RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.updateSort(id, ctx.buffetId(), sortOrder);

            audit.log("PRODUCT_RECOMMENDATION_SORT", "product_recommendation", id, Map.of(
                    "buffetId", ctx.buffetId(),
                    "productId", productId,
                    "sortOrder", sortOrder
            ));
            ra.addFlashAttribute("ok", "Порядок обновлён");
        } catch (Exception e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/admin-buffet/recommendations?productId=" + productId;
    }

    @PostMapping("/{id}/toggle")
    public String toggle(@PathVariable Long id,
                         @RequestParam("productId") Long productId,
                         Authentication auth,
                         RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.toggle(id, ctx.buffetId());

            audit.log("PRODUCT_RECOMMENDATION_TOGGLE", "product_recommendation", id, Map.of(
                    "buffetId", ctx.buffetId(),
                    "productId", productId
            ));
            ra.addFlashAttribute("ok", "Статус изменён");
        } catch (Exception e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/admin-buffet/recommendations?productId=" + productId;
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id,
                         @RequestParam("productId") Long productId,
                         Authentication auth,
                         RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.delete(id, ctx.buffetId());

            audit.log("PRODUCT_RECOMMENDATION_DELETE", "product_recommendation", id, Map.of(
                    "buffetId", ctx.buffetId(),
                    "productId", productId
            ));
            ra.addFlashAttribute("ok", "Рекомендация удалена");
        } catch (Exception e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/admin-buffet/recommendations?productId=" + productId;
    }
}
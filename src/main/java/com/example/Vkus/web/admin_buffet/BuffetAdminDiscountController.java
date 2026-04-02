package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.BuffetAdminDiscountService;
import com.example.Vkus.web.dto.ProductDiscountForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin-buffet/discounts")
public class BuffetAdminDiscountController {

    private final BuffetAdminDiscountService svc;
    private final AuditLogService audit;

    public BuffetAdminDiscountController(BuffetAdminDiscountService svc, AuditLogService audit) {
        this.svc = svc;
        this.audit = audit;
    }

    @GetMapping
    public String page(@RequestParam(value = "edit", required = false) Long editId,
                       @RequestParam(value = "ok", required = false) String ok,
                       Authentication auth,
                       Model model) {

        var ctx = svc.requireCtx(auth);

        model.addAttribute("ok", ok);
        model.addAttribute("discounts", svc.list(ctx.buffetId()));
        model.addAttribute("products", svc.productsInBuffet(ctx.buffetId()));

        if (editId != null) {
            var d = svc.requireOwned(editId, ctx.buffetId());
            model.addAttribute("form", svc.toForm(d));
            model.addAttribute("editId", editId);
        } else {
            ProductDiscountForm f = new ProductDiscountForm();
            f.setStartAt(LocalDateTime.now().plusMinutes(5).withSecond(0).withNano(0));            f.setIsActive(true);
            model.addAttribute("form", f);
            model.addAttribute("editId", null);
        }

        return "admin-buffet/discounts/list";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") ProductDiscountForm form,
                       BindingResult br,
                       Authentication auth,
                       Model model,
                       RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        boolean isCreate = (form.getId() == null);

        svc.save(form, br, ctx.buffetId(), ctx.userId());

        if (br.hasErrors()) {
            model.addAttribute("discounts", svc.list(ctx.buffetId()));
            model.addAttribute("products", svc.productsInBuffet(ctx.buffetId()));
            model.addAttribute("editId", form.getId());
            return "admin-buffet/discounts/list";
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("buffetId", ctx.buffetId());
        meta.put("productId", form.getProductId());
        meta.put("percent", form.getPercent());
        meta.put("startAt", form.getStartAt());
        meta.put("endAt", form.getEndAt());
        meta.put("isActive", form.getIsActive());

        // Если сервис не возвращает id при создании — entityId может остаться null (это ок)
        audit.log(isCreate ? "DISCOUNT_CREATE" : "DISCOUNT_UPDATE",
                "product_discount",
                form.getId(),
                meta);

        ra.addFlashAttribute("ok", "Сохранено");
        return "redirect:/admin-buffet/discounts";
    }

    @PostMapping("/deactivate")
    public String deactivate(@RequestParam("id") Long id,
                             Authentication auth,
                             RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);
        svc.deactivate(id, ctx.buffetId());

        audit.log("DISCOUNT_DEACTIVATE", "product_discount", id, Map.of(
                "buffetId", ctx.buffetId(),
                "discountId", id
        ));

        ra.addFlashAttribute("ok", "Отключено");
        return "redirect:/admin-buffet/discounts";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         Authentication auth,
                         RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);
        svc.deleteHard(id, ctx.buffetId());

        audit.log("DISCOUNT_DELETE", "product_discount", id, Map.of(
                "buffetId", ctx.buffetId(),
                "discountId", id
        ));

        ra.addFlashAttribute("ok", "Удалено");
        return "redirect:/admin-buffet/discounts";
    }
}
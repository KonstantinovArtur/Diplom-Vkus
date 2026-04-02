package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.BuffetAdminMonthlyOfferService;
import com.example.Vkus.web.dto.MonthlyOfferForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin-buffet/monthly-discount")
public class BuffetAdminMonthlyOfferController {

    private final BuffetAdminMonthlyOfferService svc;
    private final AuditLogService audit;

    public BuffetAdminMonthlyOfferController(BuffetAdminMonthlyOfferService svc, AuditLogService audit) {
        this.svc = svc;
        this.audit = audit;
    }

    @GetMapping
    public String page(@RequestParam(value = "year", required = false) Integer year,
                       @RequestParam(value = "month", required = false) Integer month,
                       Authentication auth,
                       Model model) {

        var ctx = svc.requireCtx(auth);

        LocalDate now = LocalDate.now();
        int y = (year != null ? year : now.getYear());
        int m = (month != null ? month : now.getMonthValue());

        var offer = svc.getOrCreateOfferWithFallback(ctx.buffetId(), y, m, ctx.userId());

        model.addAttribute("form", svc.toForm(offer));
        model.addAttribute("categories", svc.activeCategories());
        model.addAttribute("year", y);
        model.addAttribute("month", m);

        return "admin-buffet/monthly-discount/edit";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") MonthlyOfferForm form,
                       BindingResult br,
                       Authentication auth,
                       Model model,
                       RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        svc.save(form, br, ctx.buffetId(), ctx.userId());

        if (br.hasErrors()) {
            model.addAttribute("categories", svc.activeCategories());
            model.addAttribute("year", form.getYear());
            model.addAttribute("month", form.getMonth());
            return "admin-buffet/monthly-discount/edit";
        }

        // Логируем как “настройка скидок месяца”
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("buffetId", ctx.buffetId());
        meta.put("year", form.getYear());
        meta.put("month", form.getMonth());
        // что именно выбрали — зависит от структуры MonthlyOfferForm
        // (если у тебя там есть items/категории/проценты — Jackson нормально сериализует)
        meta.put("form", form);

        audit.log("MONTHLY_OFFER_SAVE", "monthly_discount_offer", null, meta);

        ra.addFlashAttribute("ok", "Категории месяца сохранены");
        return "redirect:/admin-buffet/monthly-discount?year=" + form.getYear() + "&month=" + form.getMonth();
    }
}
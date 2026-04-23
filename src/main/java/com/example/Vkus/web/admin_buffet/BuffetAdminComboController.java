package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.BuffetAdminComboService;
import com.example.Vkus.web.dto.ComboSlotForm;
import com.example.Vkus.web.dto.ComboSlotProductForm;
import com.example.Vkus.web.dto.ComboTemplateForm;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/admin-buffet/combos")
public class BuffetAdminComboController {

    private final BuffetAdminComboService svc;
    private final AuditLogService audit;

    public BuffetAdminComboController(BuffetAdminComboService svc, AuditLogService audit) {
        this.svc = svc;
        this.audit = audit;
    }

    private static void putIfNotNull(Map<String, Object> m, String k, Object v) {
        if (v != null) m.put(k, v);
    }

    private static Map<String, Object> meta(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (kv == null) return m;

        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object k = kv[i];
            Object v = kv[i + 1];
            if (k == null) continue;
            String key = String.valueOf(k);
            if (v != null) m.put(key, v);
        }
        return m;
    }

    @GetMapping
    public String list(@RequestParam(value = "edit", required = false) Long editId,
                       @RequestParam(value = "ok", required = false) String ok,
                       Authentication auth,
                       Model model,
                       RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        model.addAttribute("ok", ok);
        model.addAttribute("combos", svc.listTemplates(ctx.buffetId()));

        if (editId != null) {
            try {
                var t = svc.requireOwnedTemplate(editId, ctx.buffetId());
                model.addAttribute("form", svc.toForm(t));
                model.addAttribute("editId", editId);
                model.addAttribute("editCombo", t);
            } catch (IllegalStateException e) {
                ra.addFlashAttribute("err", "Комбо не найдено или недоступно для текущего буфета.");
                return "redirect:/admin-buffet/combos";
            }
        } else {
            ComboTemplateForm f = new ComboTemplateForm();
            f.setIsActive(true);
            model.addAttribute("form", f);
            model.addAttribute("editId", null);
            model.addAttribute("editCombo", null);
        }

        return "admin-buffet/combos/list";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") ComboTemplateForm form,
                       BindingResult br,
                       Authentication auth,
                       Model model,
                       RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);
        boolean isCreate = (form.getId() == null);

        try {
            svc.saveTemplate(form, br, ctx.buffetId(), ctx.userId());
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", "Комбо не найдено или недоступно для текущего буфета.");
            return "redirect:/admin-buffet/combos";
        }

        if (br.hasErrors()) {
            model.addAttribute("combos", svc.listTemplates(ctx.buffetId()));
            model.addAttribute("editId", form.getId());

            if (form.getId() != null) {
                try {
                    model.addAttribute("editCombo", svc.requireOwnedTemplate(form.getId(), ctx.buffetId()));
                } catch (IllegalStateException ignored) {
                    model.addAttribute("editCombo", null);
                }
            } else {
                model.addAttribute("editCombo", null);
            }

            return "admin-buffet/combos/list";
        }

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buffetId", ctx.buffetId());
        putIfNotNull(m, "name", form.getName());
        putIfNotNull(m, "basePrice", form.getBasePrice());
        putIfNotNull(m, "isActive", form.getIsActive());

        audit.log(isCreate ? "COMBO_TEMPLATE_CREATE" : "COMBO_TEMPLATE_UPDATE",
                "combo_template",
                form.getId(),
                m);

        ra.addFlashAttribute("ok", "Сохранено");
        return "redirect:/admin-buffet/combos";
    }

    @PostMapping("/deactivate")
    public String deactivate(@RequestParam("id") Long id,
                             Authentication auth,
                             RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.deactivateTemplate(id, ctx.buffetId());
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", "Комбо не найдено или недоступно для текущего буфета.");
            return "redirect:/admin-buffet/combos";
        }

        audit.log("COMBO_TEMPLATE_DEACTIVATE", "combo_template", id,
                meta("buffetId", ctx.buffetId(), "templateId", id));

        ra.addFlashAttribute("ok", "Отключено");
        return "redirect:/admin-buffet/combos";
    }

    @PostMapping("/delete")
    public String delete(@RequestParam("id") Long id,
                         Authentication auth,
                         RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.deleteTemplateHard(id, ctx.buffetId());

            audit.log("COMBO_TEMPLATE_DELETE", "combo_template", id,
                    meta("buffetId", ctx.buffetId(), "templateId", id));

            ra.addFlashAttribute("ok", "Удалено");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/admin-buffet/combos";
    }

    @GetMapping("/{id}")
    public String edit(@PathVariable("id") Long id,
                       Authentication auth,
                       Model model,
                       @ModelAttribute("slotForm") ComboSlotForm slotForm,
                       @ModelAttribute("slotProductForm") ComboSlotProductForm slotProductForm,
                       RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            var t = svc.requireOwnedTemplate(id, ctx.buffetId());
            model.addAttribute("t", t);
            model.addAttribute("products", svc.productsInBuffet(ctx.buffetId()));
            return "admin-buffet/combos/edit";
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", "Комбо не найдено или недоступно для текущего буфета.");
            return "redirect:/admin-buffet/combos";
        }
    }

    @PostMapping("/{id}/slots/add")
    public String addSlot(@PathVariable("id") Long templateId,
                          @Valid @ModelAttribute("slotForm") ComboSlotForm form,
                          BindingResult br,
                          Authentication auth,
                          RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.addSlot(templateId, form, br, ctx.buffetId());
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", "Комбо не найдено или недоступно для текущего буфета.");
            return "redirect:/admin-buffet/combos";
        }

        if (br.hasErrors()) {
            ra.addFlashAttribute("slotErr", "Ошибка добавления слота");
        } else {
            audit.log("COMBO_SLOT_ADD", "combo_template", templateId, meta(
                    "buffetId", ctx.buffetId(),
                    "templateId", templateId,
                    "slotName", form.getName(),
                    "requiredQty", 1,
                    "sortOrder", form.getSortOrder()
            ));
            ra.addFlashAttribute("ok", "Слот добавлен");
        }

        return "redirect:/admin-buffet/combos/" + templateId;
    }

    @PostMapping("/slots/{slotId}/delete")
    public String deleteSlot(@PathVariable Long slotId,
                             @RequestParam("templateId") Long templateId,
                             Authentication auth,
                             RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.deleteSlot(slotId, ctx.buffetId());

            audit.log("COMBO_SLOT_DELETE", "combo_slot", slotId, meta(
                    "buffetId", ctx.buffetId(),
                    "templateId", templateId,
                    "slotId", slotId
            ));

            ra.addFlashAttribute("ok", "Слот удалён");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", e.getMessage());
            if (e.getMessage() != null && e.getMessage().contains("не найден")) {
                return "redirect:/admin-buffet/combos";
            }
        }

        return "redirect:/admin-buffet/combos/" + templateId;
    }

    @PostMapping("/slots/{slotId}/products/add")
    public String addSlotProduct(@PathVariable Long slotId,
                                 @RequestParam("templateId") Long templateId,
                                 @Valid @ModelAttribute("slotProductForm") ComboSlotProductForm form,
                                 BindingResult br,
                                 Authentication auth,
                                 RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.addSlotProduct(slotId, form, br, ctx.buffetId());
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", "Слот или комбо недоступны для текущего буфета.");
            return "redirect:/admin-buffet/combos";
        }

        if (br.hasErrors()) {
            ra.addFlashAttribute("prodErr", "Не удалось добавить товар (проверь выбор, ассортимент или доплату)");
        } else {
            audit.log("COMBO_SLOT_PRODUCT_ADD", "combo_slot", slotId, meta(
                    "buffetId", ctx.buffetId(),
                    "templateId", templateId,
                    "slotId", slotId,
                    "productId", form.getProductId(),
                    "extraPrice", form.getExtraPrice()
            ));
            ra.addFlashAttribute("ok", "Товар добавлен в слот");
        }

        return "redirect:/admin-buffet/combos/" + templateId;
    }

    @PostMapping("/slots/{slotId}/products/remove")
    public String removeSlotProduct(@PathVariable Long slotId,
                                    @RequestParam("templateId") Long templateId,
                                    @RequestParam("productId") Long productId,
                                    Authentication auth,
                                    RedirectAttributes ra) {

        var ctx = svc.requireCtx(auth);

        try {
            svc.removeSlotProduct(slotId, productId, ctx.buffetId());
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", "Слот или комбо недоступны для текущего буфета.");
            return "redirect:/admin-buffet/combos";
        }

        audit.log("COMBO_SLOT_PRODUCT_REMOVE", "combo_slot", slotId, meta(
                "buffetId", ctx.buffetId(),
                "templateId", templateId,
                "slotId", slotId,
                "productId", productId
        ));

        ra.addFlashAttribute("ok", "Товар удалён из слота");
        return "redirect:/admin-buffet/combos/" + templateId;
    }
}
package com.example.Vkus.web.buyer;

import com.example.Vkus.entity.ComboSlot;
import com.example.Vkus.repository.ComboTemplateRepository;
import com.example.Vkus.security.CurrentUserFacade;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.CartComboService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/combos")
public class BuyerComboController {

    private final CurrentUserFacade currentUser;
    private final ComboTemplateRepository comboTemplateRepository;
    private final CartComboService cartComboService;
    private final AuditLogService audit;

    public BuyerComboController(CurrentUserFacade currentUser,
                                ComboTemplateRepository comboTemplateRepository,
                                CartComboService cartComboService,
                                AuditLogService audit) {
        this.currentUser = currentUser;
        this.comboTemplateRepository = comboTemplateRepository;
        this.cartComboService = cartComboService;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        Long buffetId = currentUser.requireBuffetId();
        model.addAttribute("combos", comboTemplateRepository.findActiveByBuffet(buffetId));
        return "buyer/combos";
    }

    @GetMapping("/{id}")
    public String details(@PathVariable Long id,
                          Model model,
                          RedirectAttributes ra) {

        Long buffetId = currentUser.requireBuffetId();

        var tpl = comboTemplateRepository.findByIdFullAndBuffet(id, buffetId).orElse(null);
        if (tpl == null) {
            ra.addFlashAttribute("msg", "Это комбо недоступно для текущего буфета.");
            return "redirect:/combos";
        }

        model.addAttribute("tpl", tpl);

        var slotsSorted = tpl.getSlots().stream()
                .sorted(Comparator
                        .comparing(ComboSlot::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(ComboSlot::getId))
                .toList();

        model.addAttribute("slotsSorted", slotsSorted);
        return "buyer/combo-detail";
    }

    @PostMapping("/{id}/add-to-cart")
    public String addToCart(@PathVariable Long id,
                            @RequestParam(defaultValue = "1") int qty,
                            @RequestParam Map<String, String> params,
                            RedirectAttributes ra) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        var tpl = comboTemplateRepository.findByIdFullAndBuffet(id, buffetId).orElse(null);
        if (tpl == null) {
            ra.addFlashAttribute("msg", "Это комбо недоступно для текущего буфета.");
            return "redirect:/combos";
        }

        Map<Long, Long> selected = new HashMap<>();
        for (var e : params.entrySet()) {
            String k = e.getKey();
            if (!k.startsWith("slot_")) continue;
            Long slotId = Long.valueOf(k.substring("slot_".length()));
            Long productId = Long.valueOf(e.getValue());
            selected.put(slotId, productId);
        }

        cartComboService.addCombo(userId, buffetId, id, qty, selected);

        audit.log("CART_ADD_COMBO", "combo_template", id, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId,
                "qty", qty,
                "selected", selected
        ));

        return "redirect:/cart";
    }
}
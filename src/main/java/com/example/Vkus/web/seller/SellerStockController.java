package com.example.Vkus.web.seller;

import com.example.Vkus.entity.Buffet;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.MailService;
import com.example.Vkus.service.SellerStockService;
import com.example.Vkus.service.WarehouseUserLookupService;
import com.example.Vkus.web.dto.WarehouseMailForm;
import com.example.Vkus.web.dto.WarehouseUserOption;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/seller")
public class SellerStockController {

    private final SellerStockService stockService;
    private final CurrentUserService currentUserService;
    private final WarehouseUserLookupService warehouseUserLookupService;
    private final MailService mailService;
    private final AuditLogService audit;
    private final BuffetRepository buffetRepository;

    public SellerStockController(SellerStockService stockService,
                                 CurrentUserService currentUserService,
                                 WarehouseUserLookupService warehouseUserLookupService,
                                 MailService mailService,
                                 AuditLogService audit,
                                 BuffetRepository buffetRepository) {
        this.stockService = stockService;
        this.currentUserService = currentUserService;
        this.warehouseUserLookupService = warehouseUserLookupService;
        this.mailService = mailService;
        this.audit = audit;
        this.buffetRepository = buffetRepository;
    }

    @GetMapping
    public String sellerHome() {
        return "redirect:/seller/stocks";
    }

    @GetMapping("/stocks")
    public String stocks(Model model,
                         @ModelAttribute("warehouseMailForm") WarehouseMailForm form) {

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        model.addAttribute("rows", stockService.getStocks(buffetId));
        model.addAttribute("buffetId", buffetId);

        List<WarehouseUserOption> whUsers = warehouseUserLookupService.getWarehouseUsers();
        model.addAttribute("warehouseUsers", whUsers);

        Buffet buffet = buffetRepository.findById(buffetId)
                .orElseThrow(() -> new IllegalStateException("Буфет не найден: " + buffetId));
        String buffetName = buffet.getName();

        if (form.getSubject() == null || form.getSubject().isBlank()) {
            form.setSubject("Запрос закупки и доставки для буфета \"" + buffetName + "\"");
        }
        if (form.getBody() == null || form.getBody().isBlank()) {
            form.setBody(
                    "Здравствуйте!\n\n" +
                            "Прошу закупить и доставить товары для буфета \"" + buffetName + "\".\n" +
                            "Остатки/потребность уточню при необходимости.\n\n" +
                            "Спасибо!"
            );
        }

        return "seller/stocks/list";
    }

    @PostMapping("/stocks/request-warehouse")
    public String requestWarehouse(@Valid @ModelAttribute("warehouseMailForm") WarehouseMailForm form,
                                   BindingResult bindingResult,
                                   Model model,
                                   RedirectAttributes ra) {

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        Long actorId = currentUserService.getCurrentUser().getId();

        if (bindingResult.hasErrors()) {
            model.addAttribute("rows", stockService.getStocks(buffetId));
            model.addAttribute("buffetId", buffetId);
            model.addAttribute("warehouseUsers", warehouseUserLookupService.getWarehouseUsers());
            return "seller/stocks/list";
        }

        WarehouseUserOption target = warehouseUserLookupService.getWarehouseUsers().stream()
                .filter(u -> u.id().equals(form.getWarehouseUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Сотрудник склада не найден"));

        String warehouseEmail = target.email();
        String sellerEmail = currentUserService.getCurrentUser().getEmail();

        mailService.sendPlainText(warehouseEmail, form.getSubject(), form.getBody());

        if (sellerEmail != null && !sellerEmail.equalsIgnoreCase(warehouseEmail)) {
            mailService.sendPlainText(sellerEmail, form.getSubject(), form.getBody());
        }

        audit.log("WAREHOUSE_REQUEST_EMAIL", "buffet", buffetId, Map.of(
                "mail", snapshotMail(form, target, buffetId, actorId, sellerEmail),
                "actorUserId", actorId
        ));

        ra.addFlashAttribute("msg", "Письмо отправлено: " + target.label());
        return "redirect:/seller/stocks";
    }

    private Map<String, Object> snapshotMail(WarehouseMailForm form, WarehouseUserOption target,
                                             Long buffetId, Long actorId, String sellerEmail) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buffetId", buffetId);
        m.put("actorUserId", actorId);
        m.put("warehouseUserId", form.getWarehouseUserId());
        m.put("warehouseEmail", target.email());
        m.put("sellerEmail", sellerEmail);
        m.put("subject", cut(form.getSubject(), 200));
        m.put("body", cut(form.getBody(), 2000));
        return m;
    }

    private String cut(String s, int max) {
        if (s == null) return null;
        if (s.length() <= max) return s;
        return s.substring(0, max) + "...";
    }
}
package com.example.Vkus.web.seller;

import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.InventoryBatchConsumptionService;
import com.example.Vkus.web.dto.ManualSaleForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/seller/manual-sale")
public class SellerManualSaleController {

    private final CurrentUserService currentUserService;
    private final ProductRepository productRepository;
    private final InventoryBatchConsumptionService batchService;
    private final AuditLogService audit;

    public SellerManualSaleController(CurrentUserService currentUserService,
                                      ProductRepository productRepository,
                                      InventoryBatchConsumptionService batchService,
                                      AuditLogService audit) {
        this.currentUserService = currentUserService;
        this.productRepository = productRepository;
        this.batchService = batchService;
        this.audit = audit;
    }

    @GetMapping
    public String page(Model model, @ModelAttribute("form") ManualSaleForm form) {
        model.addAttribute("products", productRepository.findByIsActiveTrueOrderByNameAsc());
        return "seller/manual_sale";
    }

    @PostMapping
    public String doSale(@Valid @ModelAttribute("form") ManualSaleForm form,
                         BindingResult br,
                         Model model,
                         RedirectAttributes ra) {

        if (br.hasErrors()) {
            model.addAttribute("products", productRepository.findByIsActiveTrueOrderByNameAsc());
            return "seller/manual_sale";
        }

        long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        long actorId = currentUserService.getCurrentUser().getId();

        var takes = batchService.consumeFromBatches(buffetId, form.getProductId(), form.getQty());

        for (var t : takes) {
            batchService.insertMovement(
                    buffetId,
                    form.getProductId(),
                    t.batchId(),
                    "sale",
                    -t.qtyTaken(),
                    actorId,
                    "manual",
                    null
            );
        }

        audit.log("MANUAL_SALE", "product", form.getProductId(), Map.of(
                "sale", snapshotManualSale(form, buffetId, actorId, takes)
        ));

        ra.addFlashAttribute("ok", "Продажа (тест) выполнена: списано " + form.getQty());
        return "redirect:/seller/manual-sale";
    }

    private Map<String, Object> snapshotManualSale(ManualSaleForm form, long buffetId, long actorId, Object takes) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buffetId", buffetId);
        m.put("actorUserId", actorId);
        m.put("productId", form.getProductId());
        m.put("qty", form.getQty());
        m.put("takes", takes); // обычно это список record’ов; после JavaTimeModule сериализуется нормально
        return m;
    }
}
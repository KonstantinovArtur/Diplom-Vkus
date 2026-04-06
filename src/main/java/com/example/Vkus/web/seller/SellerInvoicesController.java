package com.example.Vkus.web.seller;

import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.SellerInvoicesService;
import com.example.Vkus.web.dto.SellerInvoiceCheckForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/seller/invoices")
public class SellerInvoicesController {

    private final SellerInvoicesService sellerInvoicesService;
    private final CurrentUserService currentUserService;
    private final AuditLogService audit;

    public SellerInvoicesController(SellerInvoicesService sellerInvoicesService,
                                    CurrentUserService currentUserService,
                                    AuditLogService audit) {
        this.sellerInvoicesService = sellerInvoicesService;
        this.currentUserService = currentUserService;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        model.addAttribute("buffetId", buffetId);
        model.addAttribute("rows", sellerInvoicesService.listForBuffet(buffetId));
        return "seller/invoices/list";
    }

    @GetMapping("/{id}")
    public String view(@PathVariable("id") Long invoiceId,
                       Model model,
                       @ModelAttribute("form") SellerInvoiceCheckForm form,
                       RedirectAttributes ra) {

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        final SellerInvoicesService.InvoiceHeader header;
        final java.util.List<SellerInvoicesService.InvoiceItemRow> items;

        try {
            header = sellerInvoicesService.getHeaderForSeller(invoiceId, buffetId);
            items = sellerInvoicesService.getItems(invoiceId);
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("msg", e.getMessage());
            return "redirect:/seller/invoices";
        }

        if (form.getInvoiceId() == null) {
            form.setInvoiceId(invoiceId);
            for (var it : items) {
                SellerInvoiceCheckForm.Item fi = new SellerInvoiceCheckForm.Item();
                fi.setInvoiceItemId(it.id());
                fi.setReceivedQty(it.receivedQty() != null ? it.receivedQty() : it.qty());
                fi.setAcceptanceReason(it.acceptanceReason() != null ? it.acceptanceReason() : "ok");
                form.getItems().add(fi);
            }
        }

        model.addAttribute("header", header);
        model.addAttribute("items", items);
        model.addAttribute("form", form);
        return "seller/invoices/view";
    }

    @PostMapping("/{id}/check")
    public String check(@PathVariable("id") Long invoiceId,
                        @Valid @ModelAttribute("form") SellerInvoiceCheckForm form,
                        BindingResult br,
                        Model model,
                        RedirectAttributes ra) {

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        if (br.hasErrors()) {
            try {
                var header = sellerInvoicesService.getHeaderForSeller(invoiceId, buffetId);
                var items = sellerInvoicesService.getItems(invoiceId);
                model.addAttribute("header", header);
                model.addAttribute("items", items);
                return "seller/invoices/view";
            } catch (IllegalStateException e) {
                ra.addFlashAttribute("msg", e.getMessage());
                return "redirect:/seller/invoices";
            }
        }

        Long sellerUserId = currentUserService.getCurrentUser().getId();

        final SellerInvoicesService.InvoiceHeader beforeHeader;
        try {
            beforeHeader = sellerInvoicesService.getHeaderForSeller(invoiceId, buffetId);
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("msg", e.getMessage());
            return "redirect:/seller/invoices";
        }

        try {
            sellerInvoicesService.checkInvoice(invoiceId, buffetId, sellerUserId, form.getItems());
        } catch (IllegalStateException e) {
            try {
                var header = sellerInvoicesService.getHeaderForSeller(invoiceId, buffetId);
                var items = sellerInvoicesService.getItems(invoiceId);
                model.addAttribute("header", header);
                model.addAttribute("items", items);
                model.addAttribute("msg", e.getMessage());
                return "seller/invoices/view";
            } catch (IllegalStateException ex) {
                ra.addFlashAttribute("msg", ex.getMessage());
                return "redirect:/seller/invoices";
            }
        }

        final SellerInvoicesService.InvoiceHeader afterHeader;
        try {
            afterHeader = sellerInvoicesService.getHeaderForSeller(invoiceId, buffetId);
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("msg", "Накладная была проверена, но после смены буфета текущая запись недоступна.");
            return "redirect:/seller/invoices";
        }

        audit.log("SELLER_INVOICE_CHECK", "invoice", invoiceId, Map.of(
                "before", snapshotAny(beforeHeader),
                "after", snapshotAny(afterHeader),
                "checkForm", snapshotCheckForm(form),
                "actorUserId", sellerUserId,
                "buffetId", buffetId
        ));

        ra.addFlashAttribute("msg", "Накладная проверена и переведена в статус checked.");
        return "redirect:/seller/invoices/" + invoiceId;
    }

    private Map<String, Object> snapshotCheckForm(SellerInvoiceCheckForm form) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("invoiceId", form.getInvoiceId());
        m.put("itemsCount", form.getItems() != null ? form.getItems().size() : 0);

        var items = new java.util.ArrayList<Map<String, Object>>();
        if (form.getItems() != null) {
            for (var it : form.getItems()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("invoiceItemId", it.getInvoiceItemId());
                row.put("receivedQty", it.getReceivedQty());
                row.put("acceptanceReason", it.getAcceptanceReason());
                items.add(row);
            }
        }
        m.put("items", items);
        return m;
    }

    private Map<String, Object> snapshotAny(Object obj) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (obj == null) {
            m.put("exists", false);
            return m;
        }
        m.put("exists", true);
        m.put("type", obj.getClass().getName());
        m.put("value", obj.toString());
        return m;
    }
}
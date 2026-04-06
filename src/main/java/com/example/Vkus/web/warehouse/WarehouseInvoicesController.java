package com.example.Vkus.web.warehouse;

import com.example.Vkus.entity.Invoice;
import com.example.Vkus.entity.InvoiceItem;
import com.example.Vkus.repository.InvoiceItemRepository;
import com.example.Vkus.repository.InvoiceRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.repository.SupplierRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.WarehouseProcurementService;
import com.example.Vkus.web.dto.InvoiceCreateForm;
import com.example.Vkus.web.dto.InvoiceItemAddForm;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/warehouse/invoices")
public class WarehouseInvoicesController {

    private final CurrentUserService currentUserService;
    private final SupplierRepository supplierRepository;
    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final ProductRepository productRepository;
    private final WarehouseProcurementService procurementService;
    private final AuditLogService audit;

    public WarehouseInvoicesController(CurrentUserService currentUserService,
                                       SupplierRepository supplierRepository,
                                       InvoiceRepository invoiceRepository,
                                       InvoiceItemRepository invoiceItemRepository,
                                       ProductRepository productRepository,
                                       WarehouseProcurementService procurementService,
                                       AuditLogService audit) {
        this.currentUserService = currentUserService;
        this.supplierRepository = supplierRepository;
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.productRepository = productRepository;
        this.procurementService = procurementService;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        model.addAttribute("invoices", invoiceRepository.findByBuffetIdOrderByCreatedAtDesc(buffetId));
        return "warehouse/invoices/list";
    }

    @GetMapping("/new")
    public String createForm(Model model, @ModelAttribute("form") InvoiceCreateForm form) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        model.addAttribute("suppliers", supplierRepository.findAll());
        model.addAttribute("openedBuffetId", buffetId);
        return "warehouse/invoices/new";
    }

    @PostMapping("/new")
    public String create(@Valid @ModelAttribute("form") InvoiceCreateForm form,
                         BindingResult br,
                         @RequestParam("openedBuffetId") Long openedBuffetId,
                         Model model,
                         RedirectAttributes ra) {

        Long currentBuffetId = currentUserService.getCurrentBuffetIdOrThrow();

        if (!Objects.equals(openedBuffetId, currentBuffetId)) {
            ra.addFlashAttribute("err", "Активный буфет был изменён. Откройте создание накладной заново.");
            return "redirect:/warehouse/invoices";
        }

        if (br.hasErrors()) {
            model.addAttribute("suppliers", supplierRepository.findAll());
            model.addAttribute("openedBuffetId", currentBuffetId);
            return "warehouse/invoices/new";
        }

        Long createdBy = currentUserService.getCurrentUser().getId();

        Invoice invoice = procurementService.createInvoice(currentBuffetId, createdBy, form);

        audit.log("INVOICE_CREATE", "invoice", invoice.getId(), Map.of(
                "after", snapshotInvoice(invoice),
                "form", snapshotInvoiceCreateForm(form),
                "actorUserId", createdBy,
                "buffetId", currentBuffetId
        ));

        ra.addFlashAttribute("ok", "Накладная создана. Добавьте позиции.");
        return "redirect:/warehouse/invoices/" + invoice.getId();
    }

    @GetMapping("/{id}")
    public String view(@PathVariable Long id,
                       Model model,
                       @ModelAttribute("itemForm") InvoiceItemAddForm itemForm,
                       RedirectAttributes ra) {

        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        if (!buffetId.equals(inv.getBuffetId())) {
            ra.addFlashAttribute("err", "Накладная недоступна для текущего буфета.");
            return "redirect:/warehouse/invoices";
        }

        List<InvoiceItem> items = invoiceItemRepository.findByInvoice_IdOrderByIdAsc(id);

        BigDecimal totalPlannedAmount = items.stream()
                .map(it -> {
                    BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                    BigDecimal qty = BigDecimal.valueOf(it.getQty() != null ? it.getQty() : 0);
                    return price.multiply(qty);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalActualAmount = items.stream()
                .map(it -> {
                    BigDecimal price = it.getUnitPrice() != null ? it.getUnitPrice() : BigDecimal.ZERO;
                    int actualQtyInt = it.getReceivedQty() != null ? it.getReceivedQty() : (it.getQty() != null ? it.getQty() : 0);
                    BigDecimal actualQty = BigDecimal.valueOf(actualQtyInt);
                    return price.multiply(actualQty);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        boolean canEdit = "draft".equals(inv.getStatus());
        boolean canSendToCheck = "draft".equals(inv.getStatus()) && !items.isEmpty();
        boolean canPost = "checked".equals(inv.getStatus());

        model.addAttribute("invoice", inv);
        model.addAttribute("items", items);
        model.addAttribute("products", productRepository.findByIsActiveTrueOrderByNameAsc());
        model.addAttribute("totalPlannedAmount", totalPlannedAmount);
        model.addAttribute("totalActualAmount", totalActualAmount);
        model.addAttribute("totalAmount", totalActualAmount);
        model.addAttribute("canEdit", canEdit);
        model.addAttribute("canSendToCheck", canSendToCheck);
        model.addAttribute("canPost", canPost);

        return "warehouse/invoices/view";
    }

    @PostMapping("/{id}/items/add")
    public String addItem(@PathVariable Long id,
                          @Valid @ModelAttribute("itemForm") InvoiceItemAddForm itemForm,
                          BindingResult br,
                          Model model,
                          RedirectAttributes ra) {

        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        if (!buffetId.equals(inv.getBuffetId())) {
            ra.addFlashAttribute("err", "Накладная недоступна для текущего буфета.");
            return "redirect:/warehouse/invoices";
        }

        if (br.hasErrors()) {
            return view(id, model, itemForm, ra);
        }

        Map<String, Object> before = snapshotInvoice(inv);

        try {
            procurementService.addItem(id, itemForm);

            Invoice afterInv = invoiceRepository.findById(id).orElse(inv);

            audit.log("INVOICE_ITEM_ADD", "invoice", id, Map.of(
                    "before", before,
                    "after", snapshotInvoice(afterInv),
                    "itemForm", snapshotInvoiceItemForm(itemForm),
                    "actorUserId", currentUserService.getCurrentUser().getId(),
                    "buffetId", buffetId
            ));

            ra.addFlashAttribute("ok", "Позиция добавлена");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/warehouse/invoices/" + id;
    }

    @PostMapping("/{invoiceId}/items/{itemId}/delete")
    public String deleteItem(@PathVariable Long invoiceId,
                             @PathVariable Long itemId,
                             RedirectAttributes ra) {

        Invoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        if (!buffetId.equals(inv.getBuffetId())) {
            ra.addFlashAttribute("err", "Накладная недоступна для текущего буфета.");
            return "redirect:/warehouse/invoices";
        }

        Object item = invoiceItemRepository.findById(itemId).orElse(null);
        Map<String, Object> beforeItem = snapshotInvoiceItemObject(item);
        Map<String, Object> beforeInv = snapshotInvoice(inv);

        try {
            procurementService.deleteItem(itemId);

            Invoice afterInv = invoiceRepository.findById(invoiceId).orElse(inv);

            audit.log("INVOICE_ITEM_DELETE", "invoice", invoiceId, Map.of(
                    "before", beforeInv,
                    "after", snapshotInvoice(afterInv),
                    "deletedItem", beforeItem,
                    "actorUserId", currentUserService.getCurrentUser().getId(),
                    "buffetId", buffetId
            ));

            ra.addFlashAttribute("ok", "Позиция удалена");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/warehouse/invoices/" + invoiceId;
    }

    @PostMapping("/{id}/send-to-check")
    public String sendToCheck(@PathVariable Long id, RedirectAttributes ra) {

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        Long actorUserId = currentUserService.getCurrentUser().getId();

        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        if (!buffetId.equals(inv.getBuffetId())) {
            ra.addFlashAttribute("err", "Накладная недоступна для текущего буфета.");
            return "redirect:/warehouse/invoices";
        }

        Map<String, Object> before = snapshotInvoice(inv);

        try {
            procurementService.sendToSellerCheck(id, buffetId);

            Invoice afterInv = invoiceRepository.findById(id).orElse(inv);

            audit.log("INVOICE_SEND_TO_CHECK", "invoice", id, Map.of(
                    "before", before,
                    "after", snapshotInvoice(afterInv),
                    "actorUserId", actorUserId,
                    "buffetId", buffetId
            ));

            ra.addFlashAttribute("ok", "Накладная отправлена продавцу на проверку.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/warehouse/invoices/" + id;
    }

    @PostMapping("/{id}/post")
    public String post(@PathVariable Long id, RedirectAttributes ra) {

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        Long actorUserId = currentUserService.getCurrentUser().getId();

        Invoice inv = invoiceRepository.findById(id)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));
        if (!buffetId.equals(inv.getBuffetId())) {
            ra.addFlashAttribute("err", "Накладная недоступна для текущего буфета.");
            return "redirect:/warehouse/invoices";
        }

        Map<String, Object> before = snapshotInvoice(inv);

        try {
            procurementService.postInvoice(id, buffetId, actorUserId);

            Invoice afterInv = invoiceRepository.findById(id).orElse(inv);

            audit.log("INVOICE_POST", "invoice", id, Map.of(
                    "before", before,
                    "after", snapshotInvoice(afterInv),
                    "actorUserId", actorUserId,
                    "buffetId", buffetId
            ));

            ra.addFlashAttribute("ok", "Накладная проведена. Остатки обновлены.");
        } catch (IllegalStateException e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/warehouse/invoices/" + id;
    }

    private Object safeGet(Object obj, String methodName) {
        if (obj == null) return null;
        try {
            var m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception ignored) {
            return null;
        }
    }

    private Map<String, Object> snapshotInvoice(Object inv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", safeGet(inv, "getId"));
        m.put("buffetId", safeGet(inv, "getBuffetId"));
        m.put("supplierId", safeGet(inv, "getSupplierId"));
        m.put("supplierName", safeGet(inv, "getSupplierName"));
        m.put("supplier", safeGet(inv, "getSupplier"));
        m.put("docNo", safeGet(inv, "getDocNo"));
        m.put("docNumber", safeGet(inv, "getDocNumber"));
        m.put("number", safeGet(inv, "getNumber"));
        m.put("docDate", safeGet(inv, "getDocDate"));
        m.put("date", safeGet(inv, "getDate"));
        m.put("status", safeGet(inv, "getStatus"));
        m.put("createdAt", safeGet(inv, "getCreatedAt"));
        m.put("createdBy", safeGet(inv, "getCreatedBy"));
        return m;
    }

    private Map<String, Object> snapshotInvoiceCreateForm(Object f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("supplierId", safeGet(f, "getSupplierId"));
        m.put("docNo", safeGet(f, "getDocNo"));
        m.put("docNumber", safeGet(f, "getDocNumber"));
        m.put("number", safeGet(f, "getNumber"));
        m.put("docDate", safeGet(f, "getDocDate"));
        m.put("date", safeGet(f, "getDate"));
        m.put("comment", safeGet(f, "getComment"));
        m.put("note", safeGet(f, "getNote"));
        return m;
    }

    private Map<String, Object> snapshotInvoiceItemForm(InvoiceItemAddForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("productId", f.getProductId());
        m.put("qty", f.getQty());
        m.put("expiresAt", f.getExpiresAt());
        return m;
    }

    private Map<String, Object> snapshotInvoiceItemObject(Object item) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (item == null) {
            m.put("exists", false);
            return m;
        }
        m.put("exists", true);
        m.put("toString", item.toString());
        return m;
    }
}
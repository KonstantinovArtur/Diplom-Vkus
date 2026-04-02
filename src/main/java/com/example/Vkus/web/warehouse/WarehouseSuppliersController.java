package com.example.Vkus.web.warehouse;

import com.example.Vkus.entity.Supplier;
import com.example.Vkus.repository.SupplierRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.SupplierPriceImportService;
import com.example.Vkus.web.dto.SupplierForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/warehouse/suppliers")
public class WarehouseSuppliersController {

    private final SupplierRepository supplierRepository;
    private final CurrentUserService currentUserService;
    private final AuditLogService audit;
    private final SupplierPriceImportService supplierPriceImportService;

    public WarehouseSuppliersController(SupplierRepository supplierRepository,
                                        CurrentUserService currentUserService,
                                        AuditLogService audit,
                                        SupplierPriceImportService supplierPriceImportService) {
        this.supplierRepository = supplierRepository;
        this.currentUserService = currentUserService;
        this.audit = audit;
        this.supplierPriceImportService = supplierPriceImportService;
    }

    @GetMapping
    public String page(@RequestParam(name = "edit", required = false) Long editId,
                       Model model,
                       @ModelAttribute("form") SupplierForm form) {

        model.addAttribute("suppliers", supplierRepository.findAll());

        if (editId != null && (form.getId() == null)) {
            Supplier s = supplierRepository.findById(editId).orElse(null);
            if (s != null) {
                SupplierForm f = new SupplierForm();
                f.setId(s.getId());
                f.setName(s.getName());
                f.setPhone(s.getPhone());
                f.setEmail(s.getEmail());
                f.setComment(s.getComment());
                model.addAttribute("form", f);
                model.addAttribute("editId", editId);
            }
        } else if (editId != null) {
            model.addAttribute("editId", editId);
        }

        return "warehouse/suppliers/list";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") SupplierForm form,
                       BindingResult br,
                       RedirectAttributes ra,
                       Model model) {

        if (br.hasErrors()) {
            model.addAttribute("suppliers", supplierRepository.findAll());
            model.addAttribute("editId", form.getId());
            return "warehouse/suppliers/list";
        }

        if (form.getId() == null && supplierRepository.existsByNameIgnoreCase(form.getName().trim())) {
            br.rejectValue("name", "dup", "Поставщик с таким названием уже существует");
            model.addAttribute("suppliers", supplierRepository.findAll());
            return "warehouse/suppliers/list";
        }

        Supplier s;
        Map<String, Object> before = null;

        if (form.getId() == null) {
            s = new Supplier();
        } else {
            s = supplierRepository.findById(form.getId()).orElse(new Supplier());
            if (s.getId() != null) {
                before = snapshotSupplier(s);
            }
        }

        s.setName(form.getName().trim());
        s.setPhone(form.getPhone());
        s.setEmail(form.getEmail());
        s.setComment(form.getComment());

        boolean isCreate = (s.getId() == null);
        supplierRepository.save(s);

        Map<String, Object> after = snapshotSupplier(s);

        if (isCreate) {
            audit.log("SUPPLIER_CREATE", "supplier", s.getId(), Map.of(
                    "after", after,
                    "actorUserId", currentUserService.getCurrentUser().getId()
            ));
        } else {
            audit.log("SUPPLIER_UPDATE", "supplier", s.getId(), Map.of(
                    "before", before,
                    "after", after,
                    "actorUserId", currentUserService.getCurrentUser().getId()
            ));
        }

        ra.addFlashAttribute("ok", "Сохранено");
        return "redirect:/warehouse/suppliers";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Supplier s = supplierRepository.findById(id).orElse(null);
        if (s == null) {
            ra.addFlashAttribute("ok", "Уже удалено");
            return "redirect:/warehouse/suppliers";
        }

        Map<String, Object> before = snapshotSupplier(s);

        try {
            supplierRepository.deleteById(id);
            supplierRepository.flush();

            audit.log("SUPPLIER_DELETE", "supplier", id, Map.of(
                    "before", before,
                    "actorUserId", currentUserService.getCurrentUser().getId()
            ));

            ra.addFlashAttribute("ok", "Удалено");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("err",
                    "Нельзя удалить поставщика, так как он уже используется в накладных");
        }

        return "redirect:/warehouse/suppliers";
    }

    @PostMapping("/{id}/price-list/upload")
    public String uploadPriceList(@PathVariable Long id,
                                  @RequestParam("file") MultipartFile file,
                                  RedirectAttributes ra) {
        try {
            var result = supplierPriceImportService.importPriceList(id, file);

            if (result.errors().isEmpty()) {
                ra.addFlashAttribute("ok",
                        "Прайс загружен. Обновлено строк: " + result.savedCount());
            } else {
                ra.addFlashAttribute("err",
                        "Прайс загружен частично. Обновлено строк: " + result.savedCount() +
                                ". Ошибок: " + result.errors().size());
                ra.addFlashAttribute("importErrors", result.errors());
            }

            audit.log("SUPPLIER_PRICE_IMPORT", "supplier", id, Map.of(
                    "savedCount", result.savedCount(),
                    "errorsCount", result.errors().size(),
                    "actorUserId", currentUserService.getCurrentUser().getId()
            ));

        } catch (Exception e) {
            ra.addFlashAttribute("err", e.getMessage());
        }

        return "redirect:/warehouse/suppliers";
    }

    private Map<String, Object> snapshotSupplier(Supplier s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("name", s.getName());
        m.put("phone", s.getPhone());
        m.put("email", s.getEmail());
        m.put("comment", s.getComment());
        return m;
    }
}
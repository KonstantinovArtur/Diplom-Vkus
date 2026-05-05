package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.entity.Category;
import com.example.Vkus.repository.CategoryRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.CategoryForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/admin-buffet/categories")
public class AdminBuffetCategoriesController {

    private final CategoryRepository categoryRepository;
    private final AuditLogService audit;

    public AdminBuffetCategoriesController(CategoryRepository categoryRepository,
                                           AuditLogService audit) {
        this.categoryRepository = categoryRepository;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "edit", required = false) Long editId) {

        List<Category> categories = categoryRepository.findAllWithParentOrdered();
        List<Category> parentOptions = categoryRepository.findAllOrdered();

        model.addAttribute("categories", categories);
        model.addAttribute("parents", parentOptions);

        CategoryForm form = new CategoryForm();

        if (editId != null) {
            Category c = categoryRepository.findById(editId).orElse(null);
            if (c != null) {
                form.setId(c.getId());
                form.setParentId(c.getParent() != null ? c.getParent().getId() : null);
                form.setName(c.getName());
                form.setIsActive(Boolean.TRUE.equals(c.getIsActive()));
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("editId", editId);

        return "admin-buffet/categories";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") CategoryForm form,
                       BindingResult br,
                       Model model,
                       RedirectAttributes ra) {

        model.addAttribute("categories", categoryRepository.findAllWithParentOrdered());
        model.addAttribute("parents", categoryRepository.findAllOrdered());

        String name = normalizeName(form.getName());
        form.setName(name);

        Category parent = null;
        if (form.getParentId() != null) {
            parent = categoryRepository.findById(form.getParentId()).orElse(null);
            if (parent == null) {
                br.rejectValue("parentId", "parentId.invalid", "Выберите корректную родительскую категорию");
            }
        }

        if (form.getId() != null && form.getParentId() != null && form.getId().equals(form.getParentId())) {
            br.rejectValue("parentId", "parentId.self", "Нельзя выбрать категорию саму себе родителем");
        }

        if (form.getId() != null && form.getParentId() != null) {
            List<Long> subtree = categoryRepository.subtreeIds(form.getId());
            Set<Long> subSet = new HashSet<>(subtree);
            if (subSet.contains(form.getParentId())) {
                br.rejectValue("parentId", "parentId.cycle", "Нельзя выбрать потомка как родителя");
            }
        }

        if (name != null && !name.isBlank()) {
            boolean exists;

            if (form.getParentId() == null) {
                exists = (form.getId() == null)
                        ? categoryRepository.existsByParentIsNullAndNameIgnoreCase(name)
                        : categoryRepository.existsByParentIsNullAndNameIgnoreCaseAndIdNot(name, form.getId());
            } else {
                exists = (form.getId() == null)
                        ? categoryRepository.existsByParent_IdAndNameIgnoreCase(form.getParentId(), name)
                        : categoryRepository.existsByParent_IdAndNameIgnoreCaseAndIdNot(form.getParentId(), name, form.getId());
            }

            if (exists) {
                br.rejectValue(
                        "name",
                        "name.duplicate",
                        form.getParentId() == null
                                ? "Категория верхнего уровня с таким названием уже существует"
                                : "У этой родительской категории уже есть дочерняя категория с таким названием"
                );
            }
        }

        Category c = null;
        Map<String, Object> before = null;

        if (form.getId() != null) {
            c = categoryRepository.findById(form.getId()).orElse(null);
            if (c == null) {
                br.reject("notFound", "Категория не найдена");
            } else {
                before = snapshotCategory(c);
            }
        }

        if (br.hasErrors()) {
            model.addAttribute("editId", form.getId());
            return "admin-buffet/categories";
        }

        boolean isCreate = (c == null);
        if (c == null) {
            c = new Category();
        }

        c.setParent(parent);
        c.setName(name);
        c.setIsActive(Boolean.TRUE.equals(form.getIsActive()));

        categoryRepository.save(c);

        Map<String, Object> after = snapshotCategory(c);

        if (isCreate) {
            audit.log("BUFFET_CATEGORY_CREATE", "category", c.getId(), Map.of("after", after));
            ra.addFlashAttribute("ok", "Категория добавлена");
        } else {
            audit.log("BUFFET_CATEGORY_UPDATE", "category", c.getId(), Map.of("before", before, "after", after));
            ra.addFlashAttribute("ok", "Категория обновлена");
        }

        return "redirect:/admin-buffet/categories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Category c = categoryRepository.findById(id).orElse(null);
        if (c == null) {
            ra.addFlashAttribute("ok", "Категория уже удалена");
            return "redirect:/admin-buffet/categories";
        }

        Map<String, Object> before = snapshotCategory(c);
        try {
            categoryRepository.deleteById(id);
            audit.log("BUFFET_CATEGORY_DELETE", "category", id, Map.of("before", before));
            ra.addFlashAttribute("ok", "Категория удалена");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("err", "Нельзя удалить категорию: она используется товарами или другими категориями. Лучше отключите активность категории.");
        }

        return "redirect:/admin-buffet/categories";
    }

    private Map<String, Object> snapshotCategory(Category c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", c.getId());
        m.put("parentId", c.getParent() != null ? c.getParent().getId() : null);
        m.put("parentName", c.getParent() != null ? c.getParent().getName() : null);
        m.put("name", c.getName());
        m.put("isActive", c.getIsActive());
        return m;
    }

    private String normalizeName(String value) {
        if (value == null) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}

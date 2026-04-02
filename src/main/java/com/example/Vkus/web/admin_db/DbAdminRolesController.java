package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.Role;
import com.example.Vkus.repository.RoleRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.RoleForm;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/admin-db/roles")
public class DbAdminRolesController {

    private final RoleRepository roleRepository;
    private final AuditLogService audit;

    public DbAdminRolesController(RoleRepository roleRepository, AuditLogService audit) {
        this.roleRepository = roleRepository;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "edit", required = false) Long editId) {

        List<Role> roles = roleRepository.findAllByOrderByNameAsc();
        model.addAttribute("roles", roles);

        RoleForm form = new RoleForm();

        if (editId != null) {
            Role r = roleRepository.findById(editId).orElse(null);
            if (r != null) {
                form.setId(r.getId());
                form.setCode(r.getCode());
                form.setName(r.getName());
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("editId", editId);
        return "admin-db/roles";
    }

    @PostMapping("/save")
    public String save(@Valid @ModelAttribute("form") RoleForm form,
                       BindingResult br,
                       Model model,
                       RedirectAttributes ra) {

        model.addAttribute("roles", roleRepository.findAllByOrderByNameAsc());

        String code = normalizeCode(form.getCode());
        String name = normalizeName(form.getName());

        form.setCode(code);
        form.setName(name);

        if (code != null && !code.isBlank()) {
            boolean existsByCode = (form.getId() == null)
                    ? roleRepository.existsByCodeIgnoreCase(code)
                    : roleRepository.existsByCodeIgnoreCaseAndIdNot(code, form.getId());

            if (existsByCode) {
                br.rejectValue("code", "code.duplicate", "Роль с таким кодом уже существует");
            }
        }

        if (name != null && !name.isBlank()) {
            boolean existsByName = (form.getId() == null)
                    ? roleRepository.existsByNameIgnoreCase(name)
                    : roleRepository.existsByNameIgnoreCaseAndIdNot(name, form.getId());

            if (existsByName) {
                br.rejectValue("name", "name.duplicate", "Роль с таким названием уже существует");
            }
        }

        Role role = null;
        Map<String, Object> before = null;

        if (form.getId() != null) {
            role = roleRepository.findById(form.getId()).orElse(null);
            if (role == null) {
                br.reject("notFound", "Роль не найдена");
            } else {
                before = snapshotRole(role);
            }
        }

        if (br.hasErrors()) {
            model.addAttribute("editId", form.getId());
            return "admin-db/roles";
        }

        boolean isCreate = (role == null);
        if (role == null) {
            role = new Role();
        }

        role.setCode(code);
        role.setName(name);

        roleRepository.save(role);

        Map<String, Object> after = snapshotRole(role);

        if (isCreate) {
            audit.log("ROLE_CREATE", "role", role.getId(), Map.of("after", after));
            ra.addFlashAttribute("ok", "Роль добавлена");
        } else {
            audit.log("ROLE_UPDATE", "role", role.getId(), Map.of("before", before, "after", after));
            ra.addFlashAttribute("ok", "Изменения сохранены");
        }

        return "redirect:/admin-db/roles";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        Role r = roleRepository.findById(id).orElse(null);
        if (r == null) {
            ra.addFlashAttribute("ok", "Роль уже удалена");
            return "redirect:/admin-db/roles";
        }

        Map<String, Object> before = snapshotRole(r);

        try {
            roleRepository.deleteById(id);
            audit.log("ROLE_DELETE", "role", id, Map.of("before", before));
            ra.addFlashAttribute("ok", "Роль удалена");
        } catch (DataIntegrityViolationException e) {
            ra.addFlashAttribute("err", "Нельзя удалить роль: она используется у пользователей");
        }

        return "redirect:/admin-db/roles";
    }

    private Map<String, Object> snapshotRole(Role r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("code", r.getCode());
        m.put("name", r.getName());
        return m;
    }

    private String normalizeCode(String value) {
        if (value == null) return null;
        return value.trim().replaceAll("\\s+", "_").toLowerCase();
    }

    private String normalizeName(String value) {
        if (value == null) return null;
        return value.trim().replaceAll("\\s+", " ");
    }
}
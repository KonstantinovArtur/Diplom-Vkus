package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.Role;
import com.example.Vkus.entity.User;
import com.example.Vkus.entity.UserRole;
import com.example.Vkus.repository.RoleRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.repository.UserRoleRepository;
import com.example.Vkus.repository.projection.UserRow;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.UserRoleForm;
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
@RequestMapping("/admin-db/users")
public class DbAdminUsersController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogService audit;

    public DbAdminUsersController(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  UserRoleRepository userRoleRepository,
                                  AuditLogService audit) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.audit = audit;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "edit", required = false) Long editUserId) {

        List<UserRow> users = userRepository.findAllUsersWithRoles();
        List<Role> roles = roleRepository.findAllByOrderByNameAsc();

        model.addAttribute("users", users);
        model.addAttribute("roles", roles);

        UserRoleForm form = new UserRoleForm();

        if (editUserId != null) {
            User u = userRepository.findById(editUserId).orElse(null);
            if (u != null) {
                form.setUserId(u.getId());

                List<Long> roleIds = roleRepository.findRoleIdsByUserId(u.getId());
                if (!roleIds.isEmpty()) form.setRoleId(roleIds.get(0));

                model.addAttribute("editUser", u);
            }
        }

        model.addAttribute("form", form);
        model.addAttribute("editUserId", editUserId);

        return "admin-db/users";
    }

    @PostMapping("/save-role")
    public String saveRole(@Valid @ModelAttribute("form") UserRoleForm form,
                           BindingResult br,
                           Model model,
                           RedirectAttributes ra) {

        model.addAttribute("users", userRepository.findAllUsersWithRoles());
        model.addAttribute("roles", roleRepository.findAllByOrderByNameAsc());
        model.addAttribute("editUserId", form.getUserId());

        User u = null;
        if (form.getUserId() != null) {
            u = userRepository.findById(form.getUserId()).orElse(null);
        }
        if (u == null) {
            br.rejectValue("userId", "userId.invalid", "Пользователь не найден");
        } else {
            model.addAttribute("editUser", u);
        }

        Role r = null;
        if (form.getRoleId() != null) {
            r = roleRepository.findById(form.getRoleId()).orElse(null);
        }
        if (r == null) {
            br.rejectValue("roleId", "roleId.invalid", "Выберите корректную роль");
        }

        if (br.hasErrors()) {
            return "admin-db/users";
        }

        // before: какая роль была
        List<Long> oldRoleIds = roleRepository.findRoleIdsByUserId(u.getId());
        Long oldRoleId = oldRoleIds.isEmpty() ? null : oldRoleIds.get(0);
        Role oldRole = (oldRoleId != null) ? roleRepository.findById(oldRoleId).orElse(null) : null;

        // заменяем все роли пользователя на выбранную
        userRoleRepository.deleteByUserId(u.getId());

        UserRole ur = new UserRole();
        ur.setUserId(u.getId());
        ur.setRoleId(r.getId());
        userRoleRepository.save(ur);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("userId", u.getId());
        meta.put("userEmail", u.getEmail());
        meta.put("userFullName", u.getFullName());
        meta.put("beforeRoleId", oldRole != null ? oldRole.getId() : null);
        meta.put("beforeRoleCode", oldRole != null ? oldRole.getCode() : null);
        meta.put("beforeRoleName", oldRole != null ? oldRole.getName() : null);
        meta.put("afterRoleId", r.getId());
        meta.put("afterRoleCode", r.getCode());
        meta.put("afterRoleName", r.getName());

        audit.log("USER_ROLE_UPDATE", "user", u.getId(), meta);

        ra.addFlashAttribute("ok", "Роль пользователя обновлена");
        return "redirect:/admin-db/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        User u = userRepository.findById(id).orElse(null);
        if (u == null) {
            ra.addFlashAttribute("ok", "Пользователь не найден");
            return "redirect:/admin-db/users";
        }

        Map<String, Object> meta = Map.of(
                "userId", u.getId(),
                "email", u.getEmail(),
                "fullName", u.getFullName(),
                "status", u.getStatus(),
                "defaultBuffetId", u.getDefaultBuffetId()
        );

        userRepository.deleteById(id);

        audit.log("USER_DELETE", "user", id, meta);

        ra.addFlashAttribute("ok", "Пользователь удалён");
        return "redirect:/admin-db/users";
    }
}
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
import org.springframework.beans.factory.annotation.Value;
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

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_BLOCKED = "blocked";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final AuditLogService audit;
    private final long systemUserId;

    public DbAdminUsersController(UserRepository userRepository,
                                  RoleRepository roleRepository,
                                  UserRoleRepository userRoleRepository,
                                  AuditLogService audit,
                                  @Value("${vkus.jobs.expired-writeoff.system-user-id:1}") long systemUserId) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userRoleRepository = userRoleRepository;
        this.audit = audit;
        this.systemUserId = systemUserId;
    }

    @GetMapping
    public String list(Model model,
                       @RequestParam(value = "edit", required = false) Long editUserId) {

        List<UserRow> users = userRepository.findAllUsersWithRoles();
        List<Role> roles = roleRepository.findAllByOrderByNameAsc();

        model.addAttribute("users", users);
        model.addAttribute("roles", roles);
        model.addAttribute("systemUserId", systemUserId);

        UserRoleForm form = new UserRoleForm();

        if (editUserId != null) {
            User u = userRepository.findById(editUserId).orElse(null);

            if (u != null) {
                form.setUserId(u.getId());

                List<Long> roleIds = roleRepository.findRoleIdsByUserId(u.getId());

                if (!roleIds.isEmpty()) {
                    form.setRoleId(roleIds.get(0));
                }

                model.addAttribute("editUser", u);
                model.addAttribute("editUserIsSystem", isSystemUser(u));
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
        model.addAttribute("systemUserId", systemUserId);
        model.addAttribute("editUserId", form.getUserId());

        User u = null;

        if (form.getUserId() != null) {
            u = userRepository.findById(form.getUserId()).orElse(null);
        }

        if (u == null) {
            br.rejectValue("userId", "userId.invalid", "Пользователь не найден");
        } else {
            model.addAttribute("editUser", u);
            model.addAttribute("editUserIsSystem", isSystemUser(u));

            if (isSystemUser(u)) {
                ra.addFlashAttribute("err", "Роль системного пользователя SYSTEM нельзя изменять");
                return "redirect:/admin-db/users";
            }
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

        List<Long> oldRoleIds = roleRepository.findRoleIdsByUserId(u.getId());
        Long oldRoleId = oldRoleIds.isEmpty() ? null : oldRoleIds.get(0);
        Role oldRole = oldRoleId != null ? roleRepository.findById(oldRoleId).orElse(null) : null;

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

    @PostMapping("/{id}/block")
    public String block(@PathVariable Long id, RedirectAttributes ra) {
        return changeUserStatus(
                id,
                STATUS_BLOCKED,
                "USER_BLOCK",
                "Пользователь заблокирован",
                ra
        );
    }

    @PostMapping("/{id}/unblock")
    public String unblock(@PathVariable Long id, RedirectAttributes ra) {
        return changeUserStatus(
                id,
                STATUS_ACTIVE,
                "USER_UNBLOCK",
                "Пользователь разблокирован",
                ra
        );
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        User u = userRepository.findById(id).orElse(null);

        if (u == null) {
            ra.addFlashAttribute("err", "Пользователь не найден");
            return "redirect:/admin-db/users";
        }

        if (isSystemUser(u)) {
            ra.addFlashAttribute("err", "Системного пользователя SYSTEM нельзя удалить");
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

    private String changeUserStatus(Long userId,
                                    String newStatus,
                                    String auditAction,
                                    String successMessage,
                                    RedirectAttributes ra) {

        User user = userRepository.findById(userId).orElse(null);

        if (user == null) {
            ra.addFlashAttribute("err", "Пользователь не найден");
            return "redirect:/admin-db/users";
        }

        if (isSystemUser(user)) {
            ra.addFlashAttribute("err", "Системного пользователя SYSTEM нельзя блокировать или разблокировать");
            return "redirect:/admin-db/users";
        }

        String oldStatus = user.getStatus();

        if (equalsIgnoreCase(oldStatus, newStatus)) {
            ra.addFlashAttribute("ok", "Статус пользователя уже установлен");
            return "redirect:/admin-db/users";
        }

        user.setStatus(newStatus);
        userRepository.save(user);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("userId", user.getId());
        meta.put("email", user.getEmail());
        meta.put("fullName", user.getFullName());
        meta.put("beforeStatus", oldStatus);
        meta.put("afterStatus", newStatus);

        audit.log(auditAction, "user", user.getId(), meta);

        ra.addFlashAttribute("ok", successMessage);
        return "redirect:/admin-db/users";
    }

    private boolean isSystemUser(User user) {
        if (user == null) {
            return false;
        }

        if (user.getId() != null && systemUserId > 0 && user.getId() == systemUserId) {
            return true;
        }

        return equalsIgnoreCase(user.getEmail(), "SYSTEM")
                || equalsIgnoreCase(user.getFullName(), "SYSTEM");
    }

    private boolean equalsIgnoreCase(String value, String expected) {
        return value != null && value.equalsIgnoreCase(expected);
    }
}
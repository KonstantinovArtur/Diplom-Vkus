package com.example.Vkus.web;

import com.example.Vkus.entity.User;
import com.example.Vkus.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;

@ControllerAdvice
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;

    public CurrentUserModelAdvice(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * ВСЕГДА пытаемся получить email из OAuth/OIDC атрибутов.
     * НИКОГДА не используем authentication.getName() как email (там sub).
     */
    private String resolveEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return null;

        Object principalObj = authentication.getPrincipal();

        // OIDC (Google) — самый надёжный вариант
        if (principalObj instanceof OidcUser oidc) {
            String email = oidc.getEmail();
            if (email != null && !email.isBlank()) return email;
        }

        // OAuth2User (на всякий случай)
        if (principalObj instanceof OAuth2User oauth2) {
            Object email = oauth2.getAttributes().get("email");
            if (email != null) {
                String s = email.toString();
                if (!s.isBlank()) return s;
            }
        }

        // fallback — лучше вернуть null, чем sub
        return null;
    }

    /**
     * Достаём коды ролей ОДИН РАЗ на рендер страницы.
     * Потом используем их в hasXRole(...) без повторных запросов.
     */
    @ModelAttribute("currentUserRoleCodes")
    public List<String> currentUserRoleCodes(Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) return Collections.emptyList();

        List<String> codes = userRepository.findRoleCodesByEmail(email);
        return codes != null ? codes : Collections.emptyList();
    }

    @ModelAttribute("currentUserFullName")
    public String currentUserFullName(Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) return null;

        User u = userRepository.findByEmailIgnoreCase(email).orElse(null);
        return u != null ? u.getFullName() : null;
    }

    // ---- роли ----

    @ModelAttribute("hasBuyerRole")
    public boolean hasBuyerRole(@ModelAttribute("currentUserRoleCodes") List<String> codes) {
        return codes.contains("buyer");
    }

    @ModelAttribute("hasSellerRole")
    public boolean hasSellerRole(@ModelAttribute("currentUserRoleCodes") List<String> codes) {
        return codes.contains("seller");
    }

    @ModelAttribute("hasWarehouseRole")
    public boolean hasWarehouseRole(@ModelAttribute("currentUserRoleCodes") List<String> codes) {
        return codes.contains("warehouse");
    }

    @ModelAttribute("hasBuffetAdminRole")
    public boolean hasBuffetAdminRole(@ModelAttribute("currentUserRoleCodes") List<String> codes) {
        return codes.contains("buffet_admin");
    }

    @ModelAttribute("hasDbAdminRole")
    public boolean hasDbAdminRole(@ModelAttribute("currentUserRoleCodes") List<String> codes) {
        return codes.contains("db_admin");
    }
}

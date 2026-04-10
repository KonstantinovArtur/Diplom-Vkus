package com.example.Vkus.web;

import com.example.Vkus.entity.BuyerNotification;
import com.example.Vkus.entity.User;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.service.BuyerNotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Collections;
import java.util.List;

@ControllerAdvice
public class CurrentUserModelAdvice {

    private final UserRepository userRepository;
    private final BuyerNotificationService buyerNotificationService;

    public CurrentUserModelAdvice(UserRepository userRepository,
                                  BuyerNotificationService buyerNotificationService) {
        this.userRepository = userRepository;
        this.buyerNotificationService = buyerNotificationService;
    }

    /**
     * Всегда пытаемся получить email из OAuth/OIDC атрибутов.
     * authentication.getName() не используем, потому что там может быть sub.
     */
    private String resolveEmail(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }

        Object principalObj = authentication.getPrincipal();

        if (principalObj instanceof OidcUser oidc) {
            String email = oidc.getEmail();
            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        if (principalObj instanceof OAuth2User oauth2) {
            Object email = oauth2.getAttributes().get("email");
            if (email != null) {
                String s = email.toString();
                if (!s.isBlank()) {
                    return s;
                }
            }
        }

        return null;
    }

    private User loadCurrentUser(Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) {
            return null;
        }
        return userRepository.findByEmailIgnoreCase(email).orElse(null);
    }

    @ModelAttribute("currentUserId")
    public Long currentUserId(Authentication authentication) {
        User currentUser = loadCurrentUser(authentication);
        return currentUser != null ? currentUser.getId() : null;
    }

    @ModelAttribute("currentUserFullName")
    public String currentUserFullName(Authentication authentication) {
        User currentUser = loadCurrentUser(authentication);
        return currentUser != null ? currentUser.getFullName() : null;
    }

    /**
     * Коды ролей получаем один раз на рендер страницы.
     */
    @ModelAttribute("currentUserRoleCodes")
    public List<String> currentUserRoleCodes(Authentication authentication) {
        String email = resolveEmail(authentication);
        if (email == null) {
            return Collections.emptyList();
        }

        List<String> codes = userRepository.findRoleCodesByEmail(email);
        return codes != null ? codes : Collections.emptyList();
    }

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

    @ModelAttribute("buyerNotifications")
    public List<BuyerNotification> buyerNotifications(
            @ModelAttribute("hasBuyerRole") boolean hasBuyerRole,
            @ModelAttribute("currentUserId") Long currentUserId) {

        if (!hasBuyerRole || currentUserId == null) {
            return Collections.emptyList();
        }

        return buyerNotificationService.getLatestForUser(currentUserId);
    }

    @ModelAttribute("buyerUnreadNotificationsCount")
    public long buyerUnreadNotificationsCount(
            @ModelAttribute("hasBuyerRole") boolean hasBuyerRole,
            @ModelAttribute("currentUserId") Long currentUserId) {

        if (!hasBuyerRole || currentUserId == null) {
            return 0;
        }

        return buyerNotificationService.countUnread(currentUserId);
    }
}
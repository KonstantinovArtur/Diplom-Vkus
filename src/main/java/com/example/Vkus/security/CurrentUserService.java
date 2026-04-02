package com.example.Vkus.security;

import com.example.Vkus.entity.User;
import com.example.Vkus.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User getCurrentUser() {
        String email = resolveEmailFromAuth();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден по email: " + email));
    }

    public Long getCurrentBuffetIdOrThrow() {
        User u = getCurrentUser();
        if (u.getDefaultBuffetId() == null) {
            throw new IllegalStateException("У пользователя не задан default_buffet_id");
        }
        return u.getDefaultBuffetId();
    }

    private String resolveEmailFromAuth() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            throw new IllegalStateException("Нет аутентификации в SecurityContext");
        }

        Object principal = auth.getPrincipal();

        // Google OAuth2: principal = OAuth2User
        if (principal instanceof OAuth2User oAuth2User) {
            Object emailAttr = oAuth2User.getAttributes().get("email");
            if (emailAttr == null) {
                // на всякий случай fallback
                emailAttr = oAuth2User.getAttributes().get("preferred_username");
            }
            if (emailAttr == null) {
                throw new IllegalStateException("OAuth2: не найден атрибут email у пользователя");
            }
            return emailAttr.toString();
        }

        // Local/stub: обычно getName() = email/username
        String name = auth.getName();
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Authentication.getName() пустой");
        }
        return name;
    }
}

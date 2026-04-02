package com.example.Vkus.service;

import com.example.Vkus.entity.User;
import com.example.Vkus.repository.RoleRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.service.OAuthUserProvisioningService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DbRolesOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuthUserProvisioningService provisioningService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public DbRolesOAuth2UserService(OAuthUserProvisioningService provisioningService,
                                    UserRepository userRepository,
                                    RoleRepository roleRepository) {
        this.provisioningService = provisioningService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) {
        OAuth2User oauthUser = super.loadUser(userRequest);

        String email = oauthUser.getAttribute("email");
        Boolean verified = oauthUser.getAttribute("email_verified");
        String name = oauthUser.getAttribute("name");
        String sub = oauthUser.getAttribute("sub");

        // доменная проверка
        boolean ok = email != null
                && email.toLowerCase().endsWith("@mpt.ru")
                && Boolean.TRUE.equals(verified)
                && sub != null;

        if (!ok) {
            // кинем исключение — Spring сам вернёт на /login?error
            throw new RuntimeException("DOMAIN_NOT_ALLOWED");
        }

        // создаём пользователя/аккаунт/роль при первом входе
        provisioningService.provisionGoogleUser(email, name, sub);

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        // берём роли из БД и превращаем в authorities вида ROLE_BUYER
        List<String> roleCodes = roleRepository.findRoleCodesByUserId(user.getId());
        Set<GrantedAuthority> authorities = roleCodes.stream()
                .map(code -> "ROLE_" + code.toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        // можно добавить “общую роль”, если хочешь:
        // authorities.add(new SimpleGrantedAuthority("ROLE_AUTHENTICATED"));

        // nameAttributeKey обычно "sub" (Google). Главное чтобы не null:
        String nameKey = "sub";
        System.out.println("=== AUTHORITIES === " + authorities);


        // возвращаем пользователя с нашими ролями
        return new DefaultOAuth2User(authorities, oauthUser.getAttributes(), nameKey);
    }
}

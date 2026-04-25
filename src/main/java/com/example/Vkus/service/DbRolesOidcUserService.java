package com.example.Vkus.service;

import com.example.Vkus.entity.User;
import com.example.Vkus.repository.RoleRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.service.OAuthUserProvisioningService;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DbRolesOidcUserService extends OidcUserService {

    private final OAuthUserProvisioningService provisioningService;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;

    public DbRolesOidcUserService(OAuthUserProvisioningService provisioningService,
                                  UserRepository userRepository,
                                  RoleRepository roleRepository) {
        this.provisioningService = provisioningService;
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest userRequest) {
        OidcUser oidcUser = super.loadUser(userRequest);

        String email = oidcUser.getAttribute("email");
        Boolean verified = oidcUser.getAttribute("email_verified");
        String name = oidcUser.getAttribute("name");
        String sub = oidcUser.getSubject();

        boolean ok = email != null
                && Boolean.TRUE.equals(verified)
                && sub != null;

        if (!ok) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_google_account"),
                    "Google account email must be verified"
            );
        }

        email = email.toLowerCase(Locale.ROOT);

        provisioningService.provisionGoogleUser(email, name, sub);

        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();

        List<String> roleCodes = roleRepository.findRoleCodesByUserId(user.getId());
        Set<GrantedAuthority> dbAuthorities = roleCodes.stream()
                .map(code -> "ROLE_" + code.toUpperCase(Locale.ROOT))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toSet());

        // Важно: возвращаем OidcUser, но с ДОБАВЛЕННЫМИ authorities
        return new org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser(
                dbAuthorities,
                oidcUser.getIdToken(),
                oidcUser.getUserInfo(),
                "sub"
        );
    }
}

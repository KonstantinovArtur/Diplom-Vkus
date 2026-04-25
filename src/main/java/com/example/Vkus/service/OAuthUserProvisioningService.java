package com.example.Vkus.service;

import com.example.Vkus.entity.AuthAccount;
import com.example.Vkus.entity.User;
import com.example.Vkus.entity.UserRole;
import com.example.Vkus.repository.AuthAccountRepository;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.repository.UserRoleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class OAuthUserProvisioningService {

    private static final Long DEFAULT_ROLE_ID = 1L;

    private final UserRepository userRepository;
    private final AuthAccountRepository authAccountRepository;
    private final UserRoleRepository userRoleRepository;
    private final BuffetRepository buffetRepository;

    public OAuthUserProvisioningService(
            UserRepository userRepository,
            AuthAccountRepository authAccountRepository,
            UserRoleRepository userRoleRepository,
            BuffetRepository buffetRepository
    ) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.userRoleRepository = userRoleRepository;
        this.buffetRepository = buffetRepository;
    }

    @Transactional
    public User provisionGoogleUser(String email, String fullName, String googleSub) {

        User user = userRepository.findByEmailIgnoreCase(email).orElse(null);
        boolean isNewUser = false;

        if (user == null) {
            isNewUser = true;

            user = new User();
            user.setCreatedAt(LocalDateTime.now());
            user.setEmail(email.toLowerCase());
            user.setFullName(fullName != null ? fullName : email);
            user.setStatus("active");
            user.setDefaultBuffetId(resolveDefaultBuffetId());

            user = userRepository.save(user);
        }

        if ("blocked".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("USER_BLOCKED");
        }

        // Если пользователь уже был создан раньше, но default_buffet_id у него пустой,
        // автоматически назначаем первый активный буфет.
        if (user.getDefaultBuffetId() == null) {
            user.setDefaultBuffetId(resolveDefaultBuffetId());
            user = userRepository.save(user);
        }

        AuthAccount authAccount =
                authAccountRepository.findByProviderAndProviderUserId("google", googleSub)
                        .orElse(null);

        if (authAccount == null) {
            AuthAccount aa = new AuthAccount();
            aa.setUser(user);
            aa.setProvider("google");
            aa.setProviderUserId(googleSub);
            aa.setCreatedAt(LocalDateTime.now());
            authAccountRepository.save(aa);
        }

        // назначаем роль покупателя при первом входе
        if (isNewUser && !userRoleRepository.existsByUserIdAndRoleId(user.getId(), DEFAULT_ROLE_ID)) {
            UserRole ur = new UserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(DEFAULT_ROLE_ID);
            userRoleRepository.save(ur);
        }

        return user;
    }

    private Long resolveDefaultBuffetId() {
        return buffetRepository.findFirstByIsActiveTrueOrderByIdAsc()
                .orElseThrow(() -> new IllegalStateException("Нет активного буфета для назначения пользователю"))
                .getId();
    }
}
package com.example.Vkus.service;

import com.example.Vkus.entity.AuthAccount;
import com.example.Vkus.entity.User;
import com.example.Vkus.entity.UserRole;
import com.example.Vkus.repository.AuthAccountRepository;
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

    public OAuthUserProvisioningService(
            UserRepository userRepository,
            AuthAccountRepository authAccountRepository,
            UserRoleRepository userRoleRepository
    ) {
        this.userRepository = userRepository;
        this.authAccountRepository = authAccountRepository;
        this.userRoleRepository = userRoleRepository;
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

            user = userRepository.save(user);
        }

        if ("blocked".equalsIgnoreCase(user.getStatus())) {
            throw new RuntimeException("USER_BLOCKED");
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


        // ⭐ назначаем роль при первом входе
        if (isNewUser && !userRoleRepository.existsByUserIdAndRoleId(user.getId(), 1L)) {
            UserRole ur = new UserRole();
            ur.setUserId(user.getId());
            ur.setRoleId(1L);
            userRoleRepository.save(ur);
        }

        return user;
    }

}


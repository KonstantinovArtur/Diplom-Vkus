package com.example.Vkus.mobile.auth;

import com.example.Vkus.entity.User;
import com.example.Vkus.mobile.auth.dto.MobileAuthResponse;
import com.example.Vkus.repository.BuffetRepository;
import com.example.Vkus.repository.RoleRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.service.BuffetLookupService;
import com.example.Vkus.service.OAuthUserProvisioningService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MobileAuthServiceTest {

    private GoogleIdTokenVerifierService googleIdTokenVerifierService;
    private OAuthUserProvisioningService provisioningService;
    private RoleRepository roleRepository;
    private UserRepository userRepository;
    private MobileJwtService mobileJwtService;
    private BuffetLookupService buffetLookupService;
    private BuffetRepository buffetRepository;
    private MobileLoginAttemptService mobileLoginAttemptService;
    private EntityManager entityManager;

    private MobileAuthService service;

    @BeforeEach
    void setUp() {
        googleIdTokenVerifierService = mock(GoogleIdTokenVerifierService.class);
        provisioningService = mock(OAuthUserProvisioningService.class);
        roleRepository = mock(RoleRepository.class);
        userRepository = mock(UserRepository.class);
        mobileJwtService = mock(MobileJwtService.class);
        buffetLookupService = mock(BuffetLookupService.class);
        buffetRepository = mock(BuffetRepository.class);
        mobileLoginAttemptService = mock(MobileLoginAttemptService.class);
        entityManager = mock(EntityManager.class);

        service = new MobileAuthService(
                googleIdTokenVerifierService,
                provisioningService,
                roleRepository,
                userRepository,
                mobileJwtService,
                buffetLookupService,
                buffetRepository,
                mobileLoginAttemptService,
                entityManager
        );
    }

    @Test
    void authenticateWithGoogle_whenUserIsActive_returnsMobileAuthResponse() {
        User user = activeUser();

        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleUserInfo(
                        "student@mpt.ru",
                        "Иван Иванов",
                        "google-sub-1"
                ));

        when(provisioningService.provisionGoogleUser(
                "student@mpt.ru",
                "Иван Иванов",
                "google-sub-1"
        )).thenReturn(user);

        when(roleRepository.findRoleCodesByUserId(7L))
                .thenReturn(List.of("buyer"));

        when(mobileJwtService.generateToken(user, List.of("buyer")))
                .thenReturn("jwt-token");

        when(mobileJwtService.getExpiresInSeconds())
                .thenReturn(3600L);

        MobileAuthResponse response = service.authenticateWithGoogle(
                "google-token",
                "127.0.0.1"
        );

        assertEquals("jwt-token", response.accessToken());
        assertEquals("Bearer", response.tokenType());
        assertEquals(3600L, response.expiresInSeconds());
        assertEquals(7L, response.user().id());
        assertEquals("student@mpt.ru", response.user().email());
        assertEquals(List.of("buyer"), response.user().roles());

        verify(mobileLoginAttemptService).onSuccess("127.0.0.1");
    }

    @Test
    void authenticateWithGoogle_whenUserIsBlocked_throwsForbidden() {
        when(googleIdTokenVerifierService.verify("google-token"))
                .thenReturn(new GoogleIdTokenVerifierService.GoogleUserInfo(
                        "blocked@mpt.ru",
                        "Заблокированный пользователь",
                        "google-sub-blocked"
                ));

        when(provisioningService.provisionGoogleUser(
                "blocked@mpt.ru",
                "Заблокированный пользователь",
                "google-sub-blocked"
        )).thenThrow(new RuntimeException("USER_BLOCKED"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.authenticateWithGoogle("google-token", "127.0.0.1")
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());

        verify(mobileJwtService, never()).generateToken(any(User.class), anyList());
        verify(mobileLoginAttemptService, atLeastOnce()).onFailure(eq("127.0.0.1"), anyString());
    }

    @Test
    void getCurrentUser_whenUserIsBlocked_throwsForbidden() {
        User blockedUser = activeUser();
        blockedUser.setStatus("blocked");

        when(userRepository.findById(7L))
                .thenReturn(Optional.of(blockedUser));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> service.getCurrentUser(jwtWithUid(7L))
        );

        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
        verify(roleRepository, never()).findRoleCodesByUserId(anyLong());
    }

    private User activeUser() {
        User user = new User();
        user.setId(7L);
        user.setEmail("student@mpt.ru");
        user.setFullName("Иван Иванов");
        user.setStatus("active");
        user.setDefaultBuffetId(1L);
        return user;
    }

    private Jwt jwtWithUid(Long userId) {
        return new Jwt(
                "test-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                Map.of("uid", userId)
        );
    }
}
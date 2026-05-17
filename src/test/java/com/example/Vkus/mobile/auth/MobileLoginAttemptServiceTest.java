package com.example.Vkus.mobile.auth;

import com.example.Vkus.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class MobileLoginAttemptServiceTest {

    private MobileLoginAttemptService service;

    @BeforeEach
    void setUp() {
        AuditLogService auditLogService = mock(AuditLogService.class);
        service = new MobileLoginAttemptService(auditLogService);
    }

    @Test
    void checkAllowed_afterFiveFailedAttempts_blocksLoginTemporarily() {
        String ip = "127.0.0.1";

        for (int i = 0; i < 5; i++) {
            service.onFailure(ip, "invalid_google_auth");
        }

        TooManyMobileAuthAttemptsException ex = assertThrows(
                TooManyMobileAuthAttemptsException.class,
                () -> service.checkAllowed(ip)
        );

        assertTrue(ex.getRetryAfterSeconds() > 0);
    }
}
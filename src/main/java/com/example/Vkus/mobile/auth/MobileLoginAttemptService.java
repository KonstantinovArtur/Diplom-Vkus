package com.example.Vkus.mobile.auth;

import com.example.Vkus.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MobileLoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(3);

    private final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    private final AuditLogService auditLogService;

    public MobileLoginAttemptService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public void checkAllowed(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null) {
            return;
        }

        Instant now = Instant.now();

        if (info.blockedUntil != null && now.isBefore(info.blockedUntil)) {
            long retryAfter = Duration.between(now, info.blockedUntil).getSeconds();

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("ip", key);
            meta.put("retry_after_seconds", retryAfter);
            meta.put("reason", "too_many_failed_mobile_auth_attempts");

            auditLogService.logSimple("MOBILE_AUTH_BLOCKED", meta);

            throw new TooManyMobileAuthAttemptsException(
                    "Слишком много неуспешных попыток входа. Повторите позже.",
                    retryAfter
            );
        }

        if (info.blockedUntil != null && !now.isBefore(info.blockedUntil)) {
            attempts.remove(key);
        }
    }

    public void onFailure(String key, String reason) {
        Instant now = Instant.now();

        AttemptInfo info = attempts.compute(key, (k, current) -> {
            AttemptInfo value = (current == null) ? new AttemptInfo() : current;

            if (value.blockedUntil != null && !now.isBefore(value.blockedUntil)) {
                value.failedCount = 0;
                value.blockedUntil = null;
            }

            value.failedCount++;

            if (value.failedCount >= MAX_ATTEMPTS) {
                value.blockedUntil = now.plus(BLOCK_DURATION);
            }

            return value;
        });

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("ip", key);
        meta.put("failed_count", info.failedCount);
        meta.put("blocked_until", info.blockedUntil == null ? null : info.blockedUntil.toString());
        meta.put("reason", reason);

        auditLogService.logSimple("MOBILE_AUTH_FAILED", meta);
    }

    public void onSuccess(String key) {
        attempts.remove(key);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("ip", key);

        auditLogService.logSimple("MOBILE_AUTH_SUCCESS", meta);
    }

    public long getRetryAfterSeconds(String key) {
        AttemptInfo info = attempts.get(key);
        if (info == null || info.blockedUntil == null) {
            return 0;
        }

        Instant now = Instant.now();
        if (!now.isBefore(info.blockedUntil)) {
            return 0;
        }

        return Duration.between(now, info.blockedUntil).getSeconds();
    }

    private static class AttemptInfo {
        private int failedCount;
        private Instant blockedUntil;
    }
}
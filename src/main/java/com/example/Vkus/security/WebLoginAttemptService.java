package com.example.Vkus.security;

import com.example.Vkus.service.AuditLogService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WebLoginAttemptService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(3);

    private final ConcurrentHashMap<String, AttemptInfo> attempts = new ConcurrentHashMap<>();
    private final AuditLogService auditLogService;

    public WebLoginAttemptService(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    public boolean isBlocked(String ip) {
        AttemptInfo info = attempts.get(ip);
        if (info == null) return false;

        Instant now = Instant.now();

        if (info.blockedUntil != null && now.isBefore(info.blockedUntil)) {
            return true;
        }

        if (info.blockedUntil != null && !now.isBefore(info.blockedUntil)) {
            attempts.remove(ip);
        }

        return false;
    }

    public long getRetryAfterSeconds(String ip) {
        AttemptInfo info = attempts.get(ip);
        if (info == null || info.blockedUntil == null) return 0;

        Instant now = Instant.now();
        if (!now.isBefore(info.blockedUntil)) return 0;

        return Duration.between(now, info.blockedUntil).getSeconds();
    }

    public void onFailure(String ip, String reason) {
        Instant now = Instant.now();

        AttemptInfo info = attempts.compute(ip, (k, current) -> {
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
        meta.put("ip", ip);
        meta.put("failed_count", info.failedCount);
        meta.put("blocked_until", info.blockedUntil == null ? null : info.blockedUntil.toString());
        meta.put("reason", reason);

        auditLogService.logSimple("WEB_AUTH_FAILED", meta);
    }

    public void onSuccess(String ip) {
        attempts.remove(ip);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("ip", ip);

        auditLogService.logSimple("WEB_AUTH_SUCCESS", meta);
    }

    private static class AttemptInfo {
        private int failedCount;
        private Instant blockedUntil;
    }
}
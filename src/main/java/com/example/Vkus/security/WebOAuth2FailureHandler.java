package com.example.Vkus.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class WebOAuth2FailureHandler implements AuthenticationFailureHandler {

    private final com.example.Vkus.security.WebLoginAttemptService webLoginAttemptService;

    public WebOAuth2FailureHandler(com.example.Vkus.security.WebLoginAttemptService webLoginAttemptService) {
        this.webLoginAttemptService = webLoginAttemptService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {

        String ip = extractClientIp(request);
        webLoginAttemptService.onFailure(ip, "oauth2_failure");

        if (webLoginAttemptService.isBlocked(ip)) {
            long retryAfter = webLoginAttemptService.getRetryAfterSeconds(ip);
            response.sendRedirect("/login?blocked=" + retryAfter);
            return;
        }

        response.sendRedirect("/login?error");
    }

    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }
}
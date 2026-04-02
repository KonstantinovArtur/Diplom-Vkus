package com.example.Vkus.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class WebLoginBlockFilter extends OncePerRequestFilter {

    private final WebLoginAttemptService webLoginAttemptService;

    public WebLoginBlockFilter(WebLoginAttemptService webLoginAttemptService) {
        this.webLoginAttemptService = webLoginAttemptService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        String path = request.getRequestURI();

        boolean loginPath =
                "/login".equals(path) ||
                        path.startsWith("/oauth2/authorization/");

        if (!loginPath) {
            filterChain.doFilter(request, response);
            return;
        }

        String ip = extractClientIp(request);

        if (webLoginAttemptService.isBlocked(ip)) {
            long retryAfter = webLoginAttemptService.getRetryAfterSeconds(ip);
            response.sendRedirect("/login?blocked=" + retryAfter);
            return;
        }

        filterChain.doFilter(request, response);
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
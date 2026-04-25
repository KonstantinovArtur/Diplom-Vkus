package com.example.Vkus.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.IOException;

@Controller
public class AuthController {

    @GetMapping("/oauth2/denied")
    public void denied(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        // корректный logout (поддерживает и null authentication тоже)
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        response.sendRedirect("/login?error");
    }

    @GetMapping("/oauth2/blocked")
    public void blocked(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        response.sendRedirect("/login?error=blocked");
    }
}

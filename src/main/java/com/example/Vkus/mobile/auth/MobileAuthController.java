package com.example.Vkus.mobile.auth;

import com.example.Vkus.mobile.auth.dto.GoogleTokenRequest;
import com.example.Vkus.mobile.auth.dto.MobileAuthResponse;
import com.example.Vkus.mobile.auth.dto.MobileBuffetOptionDto;
import com.example.Vkus.mobile.auth.dto.MobileSelectBuffetRequest;
import com.example.Vkus.mobile.auth.dto.MobileUserDto;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/mobile")
public class MobileAuthController {

    private final MobileAuthService mobileAuthService;

    public MobileAuthController(MobileAuthService mobileAuthService) {
        this.mobileAuthService = mobileAuthService;
    }

    @PostMapping("/auth/google")
    public MobileAuthResponse google(@Valid @RequestBody GoogleTokenRequest request,
                                     HttpServletRequest httpRequest) {
        String clientIp = extractClientIp(httpRequest);
        return mobileAuthService.authenticateWithGoogle(request.idToken(), clientIp);
    }

    @GetMapping("/me")
    public MobileUserDto me(@AuthenticationPrincipal Jwt jwt) {
        return mobileAuthService.getCurrentUser(jwt);
    }

    @GetMapping("/buffets")
    public List<MobileBuffetOptionDto> buffets() {
        return mobileAuthService.getAvailableBuffets();
    }

    @PostMapping("/buffet/select")
    public MobileAuthResponse selectBuffet(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody MobileSelectBuffetRequest request) {
        return mobileAuthService.selectBuffet(jwt, request.buffetId());
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

        String remoteAddr = request.getRemoteAddr();
        if (remoteAddr == null || remoteAddr.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось определить IP клиента");
        }

        return remoteAddr;
    }
}
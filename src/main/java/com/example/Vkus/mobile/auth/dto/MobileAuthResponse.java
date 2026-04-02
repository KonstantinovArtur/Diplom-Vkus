package com.example.Vkus.mobile.auth.dto;

public record MobileAuthResponse(
        String accessToken,
        String tokenType,
        long expiresInSeconds,
        MobileUserDto user
) {
}
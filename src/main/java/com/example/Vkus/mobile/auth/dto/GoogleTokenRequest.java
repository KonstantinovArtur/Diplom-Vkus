package com.example.Vkus.mobile.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequest(
        @NotBlank(message = "idToken обязателен")
        String idToken
) {
}
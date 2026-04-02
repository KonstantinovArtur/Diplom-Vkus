package com.example.Vkus.mobile.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DebugAuthRequest(
        @NotBlank(message = "email обязателен")
        @Email(message = "Некорректный email")
        String email
) {
}
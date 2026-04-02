package com.example.Vkus.mobile.auth.dto;

import jakarta.validation.constraints.NotNull;

public record MobileSelectBuffetRequest(
        @NotNull(message = "buffetId обязателен")
        Long buffetId
) {
}
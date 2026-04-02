package com.example.Vkus.mobile.cart.dto;

import jakarta.validation.constraints.NotNull;

public record MobileRemoveCartComboRequest(
        @NotNull(message = "cartComboId обязателен")
        Long cartComboId
) {
}
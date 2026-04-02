package com.example.Vkus.mobile.cart.dto;

import jakarta.validation.constraints.NotNull;

public record MobileRemoveCartItemRequest(
        @NotNull(message = "cartItemId обязателен")
        Long cartItemId
) {
}
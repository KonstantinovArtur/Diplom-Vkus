package com.example.Vkus.mobile.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MobileAddToCartRequest(
        @NotNull(message = "productId обязателен")
        Long productId,

        @Min(value = 1, message = "qty должен быть >= 1")
        Integer qty
) {
}
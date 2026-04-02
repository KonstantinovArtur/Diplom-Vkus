package com.example.Vkus.mobile.cart.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record MobileUpdateCartQtyRequest(
        @NotNull(message = "cartItemId обязателен")
        Long cartItemId,

        @Min(value = 1, message = "qty должен быть >= 1")
        Integer qty
) {
}
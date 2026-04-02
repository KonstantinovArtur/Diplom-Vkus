package com.example.Vkus.mobile.combo.dto;

import jakarta.validation.constraints.NotNull;

public record MobileComboSelectionDto(
        @NotNull(message = "slotId обязателен")
        Long slotId,

        @NotNull(message = "productId обязателен")
        Long productId
) {
}
package com.example.Vkus.mobile.combo.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record MobileAddComboToCartRequest(
        @Min(value = 1, message = "qty должен быть >= 1")
        Integer qty,

        @NotEmpty(message = "Нужно выбрать товары для слотов")
        List<@Valid MobileComboSelectionDto> selections
) {
}
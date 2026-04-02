package com.example.Vkus.mobile.discount.dto;

import jakarta.validation.constraints.NotNull;

public record MobileChooseMonthlyDiscountRequest(
        @NotNull(message = "offerItemId обязателен")
        Long offerItemId
) {
}
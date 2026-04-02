package com.example.Vkus.mobile.cart.dto;

import java.math.BigDecimal;

public record MobileCartComboSelectionDto(
        String slotName,
        String productName,
        BigDecimal extraPrice
) {
}
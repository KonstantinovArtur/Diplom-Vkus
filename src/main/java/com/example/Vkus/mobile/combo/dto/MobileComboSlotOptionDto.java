package com.example.Vkus.mobile.combo.dto;

import java.math.BigDecimal;

public record MobileComboSlotOptionDto(
        Long productId,
        String name,
        String imageUrl,
        BigDecimal extraPrice
) {
}
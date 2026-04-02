package com.example.Vkus.mobile.combo.dto;

import java.math.BigDecimal;

public record MobileComboSummaryDto(
        Long id,
        String name,
        BigDecimal basePrice
) {
}
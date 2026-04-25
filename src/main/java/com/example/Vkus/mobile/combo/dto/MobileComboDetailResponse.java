package com.example.Vkus.mobile.combo.dto;

import java.math.BigDecimal;
import java.util.List;

public record MobileComboDetailResponse(
        Long comboId,
        String name,
        BigDecimal basePrice,
        String imageUrl,
        List<MobileComboSlotDto> slots
) {
}
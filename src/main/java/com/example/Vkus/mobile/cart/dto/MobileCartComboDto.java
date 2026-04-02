package com.example.Vkus.mobile.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record MobileCartComboDto(
        Long cartComboId,
        Long comboTemplateId,
        String comboName,
        BigDecimal unitPrice,
        Integer qty,
        BigDecimal lineTotal,
        List<MobileCartComboSelectionDto> items
) {
}
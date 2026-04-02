package com.example.Vkus.mobile.cart.dto;

import java.math.BigDecimal;
import java.util.List;

public record MobileCartResponse(
        List<MobileCartItemDto> items,
        List<MobileCartComboDto> comboItems,
        Integer totalItems,
        BigDecimal totalAmount
) {
}
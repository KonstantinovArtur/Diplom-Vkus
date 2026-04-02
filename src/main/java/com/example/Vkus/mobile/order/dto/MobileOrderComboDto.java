package com.example.Vkus.mobile.order.dto;

import java.util.List;

public record MobileOrderComboDto(
        Long orderComboId,
        String comboName,
        Integer qty,
        Double comboPriceSnapshot,
        List<MobileOrderComboItemDto> items
) {
}
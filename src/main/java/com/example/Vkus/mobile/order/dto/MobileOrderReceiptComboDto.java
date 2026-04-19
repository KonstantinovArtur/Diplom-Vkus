package com.example.Vkus.mobile.order.dto;

import java.util.List;

public record MobileOrderReceiptComboDto(
        String comboName,
        Integer qty,
        Double comboPriceSnapshot,
        List<MobileOrderReceiptComboItemDto> items
) {
}
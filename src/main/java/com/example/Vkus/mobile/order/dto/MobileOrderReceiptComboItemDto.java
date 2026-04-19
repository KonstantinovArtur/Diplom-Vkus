package com.example.Vkus.mobile.order.dto;

public record MobileOrderReceiptComboItemDto(
        String slotName,
        String productName,
        Integer qty,
        Double extraPriceSnapshot
) {
}
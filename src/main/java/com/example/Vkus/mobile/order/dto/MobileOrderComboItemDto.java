package com.example.Vkus.mobile.order.dto;

public record MobileOrderComboItemDto(
        String slotName,
        String productName,
        Integer qty,
        Double extraPrice
) {
}
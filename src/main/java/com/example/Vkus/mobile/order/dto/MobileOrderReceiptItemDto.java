package com.example.Vkus.mobile.order.dto;

public record MobileOrderReceiptItemDto(
        String name,
        Integer qty,
        Double unitPrice,
        Double discountAmount,
        Double finalAmount
) {
}
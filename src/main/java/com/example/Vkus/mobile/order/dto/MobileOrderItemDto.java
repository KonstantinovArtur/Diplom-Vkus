package com.example.Vkus.mobile.order.dto;

public record MobileOrderItemDto(
        Long orderItemId,
        Long productId,
        String productName,
        Integer qty,
        Double unitPrice,
        Double discountAmount,
        Double finalLineAmount
) {
}
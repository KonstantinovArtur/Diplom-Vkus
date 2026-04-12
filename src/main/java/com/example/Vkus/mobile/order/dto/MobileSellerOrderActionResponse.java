package com.example.Vkus.mobile.order.dto;

public record MobileSellerOrderActionResponse(
        boolean success,
        String message,
        Long orderId,
        String newStatus
) {
}
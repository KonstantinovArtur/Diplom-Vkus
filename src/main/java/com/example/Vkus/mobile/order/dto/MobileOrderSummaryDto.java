package com.example.Vkus.mobile.order.dto;

public record MobileOrderSummaryDto(
        Long orderId,
        String status,
        String createdAt,
        Double finalAmount,
        String pickupCode,
        String pickupCodeExpiresAt,
        String paymentStatus
) {
}
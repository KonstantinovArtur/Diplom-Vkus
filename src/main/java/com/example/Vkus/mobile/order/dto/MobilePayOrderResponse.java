package com.example.Vkus.mobile.order.dto;

public record MobilePayOrderResponse(
        boolean success,
        String message,
        String paymentStatus
) {
}
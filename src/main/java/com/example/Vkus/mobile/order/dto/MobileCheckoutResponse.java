package com.example.Vkus.mobile.order.dto;

public record MobileCheckoutResponse(
        boolean success,
        String message,
        Long orderId
) {
}
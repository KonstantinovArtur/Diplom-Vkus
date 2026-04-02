package com.example.Vkus.mobile.order.dto;

import java.util.List;

public record MobileOrderDetailResponse(
        Long orderId,
        String status,
        String createdAt,
        Double totalAmount,
        Double discountAmount,
        Double finalAmount,
        String pickupCode,
        String pickupCodeExpiresAt,
        String paymentStatus,
        String paymentProvider,
        List<MobileOrderItemDto> items,
        List<MobileOrderComboDto> combos
) {
}
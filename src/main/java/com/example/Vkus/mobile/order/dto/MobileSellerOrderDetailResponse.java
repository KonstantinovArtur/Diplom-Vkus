package com.example.Vkus.mobile.order.dto;

import com.example.Vkus.mobile.order.dto.MobileOrderComboDto;
import com.example.Vkus.mobile.order.dto.MobileOrderItemDto;

import java.util.List;

public record MobileSellerOrderDetailResponse(
        Long orderId,
        String buyerName,
        String buyerEmail,
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
        List<MobileOrderComboDto> combos,
        boolean canToAssembling,
        boolean canToReady,
        boolean canIssue,
        boolean canCancel
) {
}
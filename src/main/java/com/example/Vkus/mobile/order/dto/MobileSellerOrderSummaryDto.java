package com.example.Vkus.mobile.order.dto;

public record MobileSellerOrderSummaryDto(
        Long orderId,
        String buyerName,
        String status,
        String createdAt,
        Double finalAmount,
        String pickupCode,
        String paymentStatus,
        boolean canToAssembling,
        boolean canToReady,
        boolean canIssue,
        boolean canCancel
) {
}
package com.example.Vkus.mobile.order.dto;

import java.util.List;

public record MobileOrderReceiptResponse(
        Long orderId,
        String receiptNumber,
        String createdAt,
        String paidAt,
        String buyerName,
        String buyerEmail,
        String buffetName,
        String pickupCode,
        Double totalAmount,
        Double discountAmount,
        Double finalAmount,
        String paymentStatus,
        String paymentProvider,
        List<MobileOrderReceiptItemDto> items,
        List<MobileOrderReceiptComboDto> combos
) {
}
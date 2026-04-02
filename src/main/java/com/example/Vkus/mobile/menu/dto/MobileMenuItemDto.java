package com.example.Vkus.mobile.menu.dto;

import java.math.BigDecimal;

public record MobileMenuItemDto(
        Long productId,
        String name,
        String description,
        String categoryName,

        BigDecimal basePrice,
        BigDecimal finalPrice,

        BigDecimal promoPercent,
        String promoText,

        BigDecimal monthlyPercent,
        String monthlyText,

        BigDecimal batchPercent,
        String batchText,

        Long stockQty,
        boolean available,

        boolean hasImage,
        String imageUrl
) {
}
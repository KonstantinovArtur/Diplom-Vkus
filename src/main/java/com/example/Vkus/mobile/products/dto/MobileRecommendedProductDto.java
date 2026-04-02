package com.example.Vkus.mobile.products.dto;

import java.math.BigDecimal;

public record MobileRecommendedProductDto(
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

        boolean hasImage
) {
}
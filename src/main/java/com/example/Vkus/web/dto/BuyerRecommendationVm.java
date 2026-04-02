package com.example.Vkus.web.dto;

import java.math.BigDecimal;

public record BuyerRecommendationVm(
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
) {}
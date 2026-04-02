package com.example.Vkus.web.dto;

import java.math.BigDecimal;

public record MenuItemVm(
        Long productId,
        String name,
        String description,
        String categoryName,

        BigDecimal basePrice,
        BigDecimal finalPrice,

        // Акция
        BigDecimal promoPercent,
        String promoText,

        // Скидка месяца
        BigDecimal monthlyPercent,
        String monthlyText,

        BigDecimal batchPercent,
        String batchText,

        boolean hasImage
) {}

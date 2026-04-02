package com.example.Vkus.web.dto;

import java.math.BigDecimal;

public record CartLineVm(
        Long productId,
        String name,
        String categoryName,
        boolean hasImage,

        int qty,
        BigDecimal basePrice,
        BigDecimal finalUnitPrice,

        BigDecimal promoPercent,
        String promoText,
        BigDecimal monthlyPercent,
        String monthlyText,

        BigDecimal batchPercent,
        String batchText,

        BigDecimal lineTotal
) {}

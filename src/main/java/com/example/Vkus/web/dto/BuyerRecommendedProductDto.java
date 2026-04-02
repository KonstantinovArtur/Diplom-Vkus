package com.example.Vkus.web.dto;

import java.math.BigDecimal;

public record BuyerRecommendedProductDto(
        Long id,
        String name,
        String description,
        BigDecimal basePrice,
        BigDecimal finalPrice,
        String imageUrlOrEndpoint,
        String badgeText
) {}
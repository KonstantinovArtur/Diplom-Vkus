package com.example.Vkus.mobile.discount.dto;

public record MobileMonthlyDiscountCategoryDto(
        Long offerItemId,
        Long categoryId,
        String categoryName,
        Double percent,
        boolean selected
) {
}
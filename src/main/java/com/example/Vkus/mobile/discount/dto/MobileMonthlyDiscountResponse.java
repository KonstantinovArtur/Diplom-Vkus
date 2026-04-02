package com.example.Vkus.mobile.discount.dto;

import java.util.List;

public record MobileMonthlyDiscountResponse(
        Long buffetId,
        Integer year,
        Integer month,
        List<MobileMonthlyDiscountCategoryDto> categories
) {
}
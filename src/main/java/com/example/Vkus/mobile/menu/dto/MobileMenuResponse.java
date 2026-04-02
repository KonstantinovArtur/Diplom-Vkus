package com.example.Vkus.mobile.menu.dto;

import java.util.List;

public record MobileMenuResponse(
        Long buffetId,
        String q,
        Long categoryId,
        List<MobileMenuCategoryDto> categories,
        List<MobileMenuItemDto> items
) {
}
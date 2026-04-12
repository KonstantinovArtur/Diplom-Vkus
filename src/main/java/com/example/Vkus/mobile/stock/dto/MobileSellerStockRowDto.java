package com.example.Vkus.mobile.stock.dto;

public record MobileSellerStockRowDto(
        Long productId,
        String name,
        Double basePrice,
        Long qty,
        boolean available
) {
}
package com.example.Vkus.mobile.cart.dto;

import java.math.BigDecimal;

public record MobileCartItemDto(
        Long cartItemId,
        Long productId,
        String name,
        String imageUrl,
        BigDecimal unitPrice,
        Integer qty,
        BigDecimal lineTotal
) {
}
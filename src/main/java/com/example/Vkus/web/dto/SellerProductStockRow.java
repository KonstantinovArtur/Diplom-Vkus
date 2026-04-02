package com.example.Vkus.web.dto;

import java.math.BigDecimal;

public record SellerProductStockRow(
        Long productId,
        String name,
        BigDecimal basePrice,
        Long qty
) {}

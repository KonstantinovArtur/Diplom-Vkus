package com.example.Vkus.mobile.stock.dto;

import java.util.List;

public record MobileSellerStocksResponse(
        Long buffetId,
        String buffetName,
        List<MobileSellerStockRowDto> rows,
        List<MobileWarehouseUserDto> warehouseUsers,
        String defaultSubject,
        String defaultBody
) {
}
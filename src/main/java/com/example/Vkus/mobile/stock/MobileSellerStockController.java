package com.example.Vkus.mobile.stock;

import com.example.Vkus.mobile.stock.dto.MobileSellerStocksResponse;
import com.example.Vkus.mobile.stock.dto.MobileSellerWarehouseMailRequest;
import com.example.Vkus.mobile.stock.dto.MobileSellerWarehouseMailResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/seller/stocks")
public class MobileSellerStockController {

    private final MobileSellerStockService mobileSellerStockService;

    public MobileSellerStockController(MobileSellerStockService mobileSellerStockService) {
        this.mobileSellerStockService = mobileSellerStockService;
    }

    @GetMapping
    public MobileSellerStocksResponse getStocks(@AuthenticationPrincipal Jwt jwt) {
        return mobileSellerStockService.getStocks(jwt);
    }

    @PostMapping("/request-warehouse")
    public MobileSellerWarehouseMailResponse requestWarehouse(@AuthenticationPrincipal Jwt jwt,
                                                              @Valid @RequestBody MobileSellerWarehouseMailRequest request) {
        return mobileSellerStockService.sendWarehouseRequest(jwt, request);
    }
}
package com.example.Vkus.web.dto;

import java.math.BigDecimal;

public interface TopProductRow {
    Long getProductId();
    String getProductName();
    Long getQty();
    BigDecimal getRevenue();
}
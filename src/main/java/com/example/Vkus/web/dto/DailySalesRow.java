package com.example.Vkus.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailySalesRow {
    LocalDate getDay();
    Long getOrdersCount();
    BigDecimal getRevenue();
}
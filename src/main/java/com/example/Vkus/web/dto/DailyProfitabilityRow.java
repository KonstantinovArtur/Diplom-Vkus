package com.example.Vkus.web.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface DailyProfitabilityRow {
    LocalDate getDay();
    BigDecimal getRevenue();
    BigDecimal getCosts();
    BigDecimal getProfit();
}
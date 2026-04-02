package com.example.Vkus.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public class MonthlyOfferForm {

    @NotNull
    private Integer year;

    @NotNull
    @Min(1) @Max(12)
    private Integer month;

    @Valid
    @Size(min = 4, max = 4, message = "Должно быть ровно 4 категории")
    private List<Item> items = new ArrayList<>();

    public static class Item {
        @NotNull(message="Выберите категорию")
        private Long categoryId;

        @NotNull(message="Процент обязателен")
        @DecimalMin(value="1.00", message="Минимум 1%")
        @DecimalMax(value="3.00", message="Максимум 3%")
        private BigDecimal percent;

        public Long getCategoryId() { return categoryId; }
        public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
        public BigDecimal getPercent() { return percent; }
        public void setPercent(BigDecimal percent) { this.percent = percent; }
    }

    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
}

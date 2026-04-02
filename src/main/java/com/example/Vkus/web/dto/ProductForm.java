package com.example.Vkus.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class ProductForm {

    private Long id;

    @NotNull(message = "Выберите категорию")
    private Long categoryId;

    @NotBlank(message = "Название обязательно")
    @Size(max = 200, message = "Название слишком длинное (до 200)")
    private String name;

    @Size(max = 2000, message = "Описание слишком длинное (до 2000)")
    private String description;

    @NotNull(message = "Цена обязательна")
    @DecimalMin(value = "0.00", inclusive = true, message = "Цена не может быть отрицательной")
    @Digits(integer = 8, fraction = 2, message = "Неверный формат цены")
    private BigDecimal basePrice;

    @Min(value = 1, message = "Срок хранения должен быть не меньше 1 дня")
    private Integer shelfLifeDays;

    private Boolean isActive = true;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public void setBasePrice(BigDecimal basePrice) {
        this.basePrice = basePrice;
    }

    public Integer getShelfLifeDays() {
        return shelfLifeDays;
    }

    public void setShelfLifeDays(Integer shelfLifeDays) {
        this.shelfLifeDays = shelfLifeDays;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}
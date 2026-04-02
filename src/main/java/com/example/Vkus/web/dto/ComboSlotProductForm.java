package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

public class ComboSlotProductForm {

    @NotNull(message = "Выберите товар")
    private Long productId;

    @PositiveOrZero(message = "Доплата должна быть >= 0")
    private BigDecimal extraPrice; // можно null (тогда без доплаты)

    // getters/setters
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }
}
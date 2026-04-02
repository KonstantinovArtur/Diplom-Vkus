package com.example.Vkus.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ManualSaleForm {

    @NotNull
    private Long productId;

    @NotNull
    @Min(1)
    private Integer qty;

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
}

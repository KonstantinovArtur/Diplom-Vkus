package com.example.Vkus.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class WriteoffCreateForm {

    @NotBlank
    private String reason; // expired / damaged / inventory_adjustment / other

    private String comment;

    @NotNull
    private Long productId;

    @NotNull
    private Long batchId;

    @NotNull
    @Min(1)
    private Integer qty;

    // getters/setters
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
}

package com.example.Vkus.web.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public class BatchDiscountForm {

    @NotNull
    private Long batchId;

    @NotNull
    @DecimalMin(value = "0.01")
    @DecimalMax(value = "99.99")
    private BigDecimal percent;

    @NotNull
    private Boolean isActive = true;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }

    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
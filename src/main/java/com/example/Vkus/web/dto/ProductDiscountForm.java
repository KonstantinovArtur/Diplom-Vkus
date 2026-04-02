package com.example.Vkus.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ProductDiscountForm {

    private Long id;

    @NotNull(message = "Выберите товар")
    private Long productId;

    @NotNull(message = "Укажите процент")
    @DecimalMin(value = "0.01", message = "Процент должен быть больше 0")
    @DecimalMax(value = "99.99", message = "Процент должен быть меньше 100")
    private BigDecimal percent;

    // Для <input type="datetime-local"> нужен именно такой формат
    @NotNull(message = "Укажите дату начала")
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startAt;

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endAt;

    @NotNull
    private Boolean isActive = true;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}

package com.example.Vkus.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public interface ProductRow {
    Long getId();
    LocalDateTime getCreatedAt();
    String getName();
    String getDescription();
    String getImageUrl();
    BigDecimal getBasePrice();
    Boolean getIsActive();

    Long getCategoryId();
    String getCategoryName();
    String getParentCategoryName(); // может быть null
}

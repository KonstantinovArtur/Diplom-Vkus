package com.example.Vkus.web.dto;

import jakarta.validation.constraints.*;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public class ProductUpsertDto {

    @NotNull(message = "Категория обязательна")
    private Long categoryId;

    @NotBlank(message = "Название обязательно")
    @Size(max = 200, message = "Максимум 200 символов")
    private String name;

    @Size(max = 1000, message = "Максимум 1000 символов")
    private String description;

    @NotNull(message = "Цена обязательна")
    @DecimalMin(value = "0.00", inclusive = true, message = "Цена должна быть >= 0")
    @Digits(integer = 10, fraction = 2, message = "Неверный формат цены")
    private BigDecimal basePrice;

    private boolean active = true;

    // image_data / image_mime / image_updated_at
    private MultipartFile image;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public MultipartFile getImage() { return image; }
    public void setImage(MultipartFile image) { this.image = image; }
}

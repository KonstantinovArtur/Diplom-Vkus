package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public class ComboTemplateForm {

    private Long id;

    @NotBlank(message = "Название обязательно")
    private String name;

    @NotNull(message = "Цена комбо обязательна")
    @PositiveOrZero(message = "Цена должна быть >= 0")
    private BigDecimal basePrice;

    @NotNull(message = "Статус обязателен")
    private Boolean isActive = true;
    private MultipartFile image;

    public MultipartFile getImage() { return image; }
    public void setImage(MultipartFile image) { this.image = image; }
    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }
}
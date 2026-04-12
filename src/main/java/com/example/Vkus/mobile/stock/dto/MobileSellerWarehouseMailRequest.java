package com.example.Vkus.mobile.stock.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record MobileSellerWarehouseMailRequest(
        @NotNull(message = "Выберите сотрудника склада")
        Long warehouseUserId,

        @NotBlank(message = "Тема обязательна")
        @Size(max = 150, message = "Тема слишком длинная")
        String subject,

        @NotBlank(message = "Текст письма обязателен")
        @Size(max = 2000, message = "Текст письма слишком длинный")
        String body
) {
}
package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class WarehouseMailForm {

    @NotNull(message = "Выберите сотрудника склада")
    private Long warehouseUserId;

    @NotBlank(message = "Тема обязательна")
    @Size(max = 150, message = "Тема слишком длинная")
    private String subject;

    @NotBlank(message = "Текст письма обязателен")
    @Size(max = 2000, message = "Текст письма слишком длинный")
    private String body;

    public Long getWarehouseUserId() { return warehouseUserId; }
    public void setWarehouseUserId(Long warehouseUserId) { this.warehouseUserId = warehouseUserId; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getBody() { return body; }
    public void setBody(String body) { this.body = body; }
}

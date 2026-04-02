package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotNull;

public class InvoiceCreateForm {

    @NotNull(message = "Выберите поставщика")
    private Long supplierId;

    public Long getSupplierId() { return supplierId; }
    public void setSupplierId(Long supplierId) { this.supplierId = supplierId; }
}

package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;

public class ComboSlotForm {

    @NotBlank(message = "Название слота обязательно")
    private String name;

    private Integer requiredQty = 1;

    private Integer sortOrder = 0;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getRequiredQty() {
        return 1;
    }

    public void setRequiredQty(Integer requiredQty) {
        this.requiredQty = 1;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }
}
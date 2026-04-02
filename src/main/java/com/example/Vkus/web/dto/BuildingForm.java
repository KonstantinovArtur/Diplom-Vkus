package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class BuildingForm {

    private Long id; // null -> create, not null -> edit

    @NotBlank(message = "Название обязательно")
    @Size(max = 255, message = "Название слишком длинное")
    private String name;

    @Size(max = 500, message = "Адрес слишком длинный")
    private String address;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
}

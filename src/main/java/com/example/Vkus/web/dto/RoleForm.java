package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class RoleForm {

    private Long id;

    @NotBlank(message = "Код роли обязателен")
    @Size(max = 50, message = "Код роли слишком длинный (макс 50)")
    @Pattern(
            regexp = "^[a-z][a-z0-9_]*$",
            message = "Код: только латиница в нижнем регистре, цифры и подчёркивания (пример: db_admin)"
    )
    private String code;

    @NotBlank(message = "Название роли обязательно")
    @Size(max = 100, message = "Название слишком длинное (макс 100)")
    private String name;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}

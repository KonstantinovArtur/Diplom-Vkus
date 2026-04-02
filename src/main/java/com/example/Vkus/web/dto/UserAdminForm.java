package com.example.Vkus.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;

import java.util.ArrayList;
import java.util.List;

public class UserAdminForm {

    private Long id;

    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный email")
    private String email;

    @NotBlank(message = "ФИО обязательно")
    private String fullName;

    @NotBlank(message = "Статус обязателен")
    @Pattern(regexp = "active|blocked", message = "Статус должен быть active или blocked")
    private String status = "active";

    @NotEmpty(message = "Нужно выбрать хотя бы одну роль")
    private List<Long> roleIds = new ArrayList<>();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName == null ? null : fullName.trim(); }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }
}

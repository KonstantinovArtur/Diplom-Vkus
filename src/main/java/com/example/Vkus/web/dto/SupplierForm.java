package com.example.Vkus.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class SupplierForm {

    private Long id;

    @NotBlank(message = "Название обязательно")
    @Size(max = 200, message = "Слишком длинное название")
    private String name;

    @Size(max = 50, message = "Слишком длинный телефон")
    private String phone;

    @Size(max = 200, message = "Слишком длинный email")
    private String email;

    @Size(max = 500, message = "Комментарий слишком длинный")
    private String comment;

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}

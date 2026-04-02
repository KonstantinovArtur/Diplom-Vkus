package com.example.Vkus.web.dto;

public record WarehouseUserOption(Long id, String fullName, String email) {
    public String label() {
        return fullName + " (" + email + ")";
    }
}

package com.example.Vkus.repository.projection;

import java.time.LocalDateTime;

public interface UserRow {
    Long getId();
    LocalDateTime getCreatedAt();
    String getEmail();
    String getFullName();
    String getStatus();
    String getRoles(); // строка "buyer, seller"
}

package com.example.Vkus.web.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record BatchOption(
        Long id,
        Integer qtyAvailable,
        LocalDate expiresAt,
        LocalDateTime receivedAt,
        String label
) {}

package com.example.Vkus.mobile.notification.dto;

public record MobileNotificationItemDto(
        Long id,
        String title,
        String message,
        boolean isRead,
        String createdAt,
        Long orderId
) {
}
package com.example.Vkus.mobile.notification.dto;

public record MobileNotificationActionResponse(
        boolean success,
        String message,
        int affectedCount
) {

}
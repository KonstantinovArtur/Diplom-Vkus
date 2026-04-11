package com.example.Vkus.mobile.notification.dto;

import java.util.List;

public record MobileNotificationsResponse(
        long unreadCount,
        List<MobileNotificationItemDto> items
) {
}

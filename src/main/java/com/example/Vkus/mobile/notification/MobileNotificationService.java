package com.example.Vkus.mobile.notification;

import com.example.Vkus.mobile.notification.dto.MobileNotificationActionResponse;
import com.example.Vkus.mobile.notification.dto.MobileNotificationItemDto;
import com.example.Vkus.mobile.notification.dto.MobileNotificationsResponse;
import com.example.Vkus.service.BuyerNotificationService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class MobileNotificationService {

    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final BuyerNotificationService buyerNotificationService;

    public MobileNotificationService(BuyerNotificationService buyerNotificationService) {
        this.buyerNotificationService = buyerNotificationService;
    }

    @Transactional(readOnly = true)
    public MobileNotificationsResponse getNotifications(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));

        var notifications = buyerNotificationService.getLatestForUser(userId);
        long unreadCount = buyerNotificationService.countUnread(userId);

        List<MobileNotificationItemDto> items = notifications.stream()
                .map(n -> new MobileNotificationItemDto(
                        n.getId(),
                        n.getTitle(),
                        n.getMessage(),
                        n.isRead(),
                        n.getCreatedAt() == null ? null : n.getCreatedAt().format(DT),
                        n.getOrder() == null ? null : n.getOrder().getId()
                ))
                .toList();

        return new MobileNotificationsResponse(unreadCount, items);
    }

    @Transactional
    public MobileNotificationActionResponse markAllAsRead(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        int updated = buyerNotificationService.markAllAsRead(userId);

        return new MobileNotificationActionResponse(
                true,
                updated > 0 ? "Уведомления отмечены как прочитанные" : "Новых уведомлений нет",
                updated
        );
    }

    @Transactional
    public MobileNotificationActionResponse clearRead(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        int deleted = buyerNotificationService.clearRead(userId);

        return new MobileNotificationActionResponse(
                true,
                deleted > 0 ? "Прочитанные уведомления очищены" : "Нет прочитанных уведомлений для очистки",
                deleted
        );
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }
}
package com.example.Vkus.web.buyer;

import com.example.Vkus.entity.BuyerNotification;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.BuyerNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/buyer/notifications")
public class BuyerNotificationController {

    private final BuyerNotificationService buyerNotificationService;
    private final CurrentUserService currentUserService;

    public BuyerNotificationController(BuyerNotificationService buyerNotificationService,
                                       CurrentUserService currentUserService) {
        this.buyerNotificationService = buyerNotificationService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/live")
    public ResponseEntity<?> live() {
        Long userId = currentUserService.getCurrentUser().getId();

        List<BuyerNotification> notifications = buyerNotificationService.getLatestForUser(userId);
        long unreadCount = buyerNotificationService.countUnread(userId);

        List<Map<String, Object>> items = notifications.stream()
                .map(n -> Map.<String, Object>of(
                        "id", n.getId(),
                        "title", n.getTitle(),
                        "message", n.getMessage(),
                        "isRead", n.isRead(),
                        "createdAt", n.getCreatedAt() != null ? n.getCreatedAt().toString() : "",
                        "orderId", n.getOrder() != null ? n.getOrder().getId() : null
                ))
                .toList();

        return ResponseEntity.ok(Map.of(
                "unreadCount", unreadCount,
                "items", items
        ));
    }

    @PostMapping("/read-all")
    public ResponseEntity<?> markAllAsRead() {
        Long userId = currentUserService.getCurrentUser().getId();
        int updated = buyerNotificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("updated", updated));
    }

    @PostMapping("/clear-read")
    public ResponseEntity<?> clearRead() {
        Long userId = currentUserService.getCurrentUser().getId();
        int deleted = buyerNotificationService.clearRead(userId);
        return ResponseEntity.ok(Map.of("deleted", deleted));
    }
}
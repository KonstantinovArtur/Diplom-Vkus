package com.example.Vkus.mobile.notification;

import com.example.Vkus.mobile.notification.dto.MobileNotificationActionResponse;
import com.example.Vkus.mobile.notification.dto.MobileNotificationsResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/notifications")
public class MobileNotificationController {

    private final MobileNotificationService mobileNotificationService;

    public MobileNotificationController(MobileNotificationService mobileNotificationService) {
        this.mobileNotificationService = mobileNotificationService;
    }

    @GetMapping
    public MobileNotificationsResponse getNotifications(@AuthenticationPrincipal Jwt jwt) {
        return mobileNotificationService.getNotifications(jwt);
    }

    @PostMapping("/read-all")
    public MobileNotificationActionResponse markAllAsRead(@AuthenticationPrincipal Jwt jwt) {
        return mobileNotificationService.markAllAsRead(jwt);
    }

    @PostMapping("/clear-read")
    public MobileNotificationActionResponse clearRead(@AuthenticationPrincipal Jwt jwt) {
        return mobileNotificationService.clearRead(jwt);
    }

}
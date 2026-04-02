package com.example.Vkus.mobile.menu;

import com.example.Vkus.mobile.menu.dto.MobileMenuResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mobile")
public class MobileMenuController {

    private final MobileMenuService mobileMenuService;

    public MobileMenuController(MobileMenuService mobileMenuService) {
        this.mobileMenuService = mobileMenuService;
    }

    @GetMapping("/menu")
    public MobileMenuResponse menu(@AuthenticationPrincipal Jwt jwt,
                                   @RequestParam(value = "q", required = false) String q,
                                   @RequestParam(value = "categoryId", required = false) Long categoryId) {
        return mobileMenuService.getMenu(jwt, q, categoryId);
    }
}
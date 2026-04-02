package com.example.Vkus.mobile.combo;

import com.example.Vkus.mobile.cart.dto.MobileCartActionResponse;
import com.example.Vkus.mobile.combo.dto.MobileAddComboToCartRequest;
import com.example.Vkus.mobile.combo.dto.MobileComboDetailResponse;
import com.example.Vkus.mobile.combo.dto.MobileComboListResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/combos")
public class MobileComboController {

    private final MobileComboService mobileComboService;

    public MobileComboController(MobileComboService mobileComboService) {
        this.mobileComboService = mobileComboService;
    }

    @GetMapping
    public MobileComboListResponse getCombos(@AuthenticationPrincipal Jwt jwt) {
        return mobileComboService.getCombos(jwt);
    }

    @GetMapping("/{comboId}")
    public MobileComboDetailResponse getComboDetail(@AuthenticationPrincipal Jwt jwt,
                                                    @PathVariable Long comboId) {
        return mobileComboService.getComboDetail(jwt, comboId);
    }

    @PostMapping("/{comboId}/add-to-cart")
    public MobileCartActionResponse addToCart(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable Long comboId,
                                              @Valid @RequestBody MobileAddComboToCartRequest request) {
        return mobileComboService.addComboToCart(jwt, comboId, request);
    }
}
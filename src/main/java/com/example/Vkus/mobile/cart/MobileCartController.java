package com.example.Vkus.mobile.cart;

import com.example.Vkus.mobile.cart.dto.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/cart")
public class MobileCartController {

    private final MobileCartService mobileCartService;

    public MobileCartController(MobileCartService mobileCartService) {
        this.mobileCartService = mobileCartService;
    }

    @GetMapping
    public MobileCartResponse getCart(@AuthenticationPrincipal Jwt jwt) {
        return mobileCartService.getCart(jwt);
    }

    @PostMapping("/add")
    public MobileCartActionResponse add(@AuthenticationPrincipal Jwt jwt,
                                        @Valid @RequestBody MobileAddToCartRequest request) {
        return mobileCartService.addToCart(jwt, request.productId(), request.qty());
    }

    @PostMapping("/update-qty")
    public MobileCartActionResponse updateQty(@AuthenticationPrincipal Jwt jwt,
                                              @Valid @RequestBody MobileUpdateCartQtyRequest request) {
        return mobileCartService.updateQty(jwt, request.cartItemId(), request.qty());
    }

    @PostMapping("/remove")
    public MobileCartActionResponse remove(@AuthenticationPrincipal Jwt jwt,
                                           @Valid @RequestBody MobileRemoveCartItemRequest request) {
        return mobileCartService.removeItem(jwt, request.cartItemId());
    }

    @PostMapping("/remove-combo")
    public MobileCartActionResponse removeCombo(@AuthenticationPrincipal Jwt jwt,
                                                @Valid @RequestBody MobileRemoveCartComboRequest request) {
        return mobileCartService.removeCombo(jwt, request.cartComboId());
    }
}
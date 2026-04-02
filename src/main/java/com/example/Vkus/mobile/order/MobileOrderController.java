package com.example.Vkus.mobile.order;

import com.example.Vkus.mobile.order.dto.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/orders")
public class MobileOrderController {

    private final MobileOrderService mobileOrderService;

    public MobileOrderController(MobileOrderService mobileOrderService) {
        this.mobileOrderService = mobileOrderService;
    }

    @PostMapping("/checkout")
    public MobileCheckoutResponse checkout(@AuthenticationPrincipal Jwt jwt) {
        return mobileOrderService.checkout(jwt);
    }

    @GetMapping("/my")
    public List<MobileOrderSummaryDto> myOrders(@AuthenticationPrincipal Jwt jwt) {
        return mobileOrderService.getMyOrders(jwt);
    }

    @GetMapping("/{orderId}")
    public MobileOrderDetailResponse detail(@AuthenticationPrincipal Jwt jwt,
                                            @PathVariable Long orderId) {
        return mobileOrderService.getOrderDetail(jwt, orderId);
    }

    @PostMapping("/{orderId}/pay")
    public MobilePayOrderResponse pay(@AuthenticationPrincipal Jwt jwt,
                                      @PathVariable Long orderId) {
        return mobileOrderService.payOrder(jwt, orderId);
    }
}
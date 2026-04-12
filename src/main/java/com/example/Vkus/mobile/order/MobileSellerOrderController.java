package com.example.Vkus.mobile.order;

import com.example.Vkus.mobile.order.dto.MobileSellerIssueOrderRequest;
import com.example.Vkus.mobile.order.dto.MobileSellerOrderActionResponse;
import com.example.Vkus.mobile.order.dto.MobileSellerOrderDetailResponse;
import com.example.Vkus.mobile.order.dto.MobileSellerOrderSummaryDto;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/seller/orders")
public class MobileSellerOrderController {

    private final MobileSellerOrderService mobileSellerOrderService;

    public MobileSellerOrderController(MobileSellerOrderService mobileSellerOrderService) {
        this.mobileSellerOrderService = mobileSellerOrderService;
    }

    @GetMapping
    public List<MobileSellerOrderSummaryDto> list(@AuthenticationPrincipal Jwt jwt) {
        return mobileSellerOrderService.getOrders(jwt);
    }

    @GetMapping("/{orderId}")
    public MobileSellerOrderDetailResponse detail(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable Long orderId) {
        return mobileSellerOrderService.getOrderDetail(jwt, orderId);
    }

    @PostMapping("/{orderId}/assembling")
    public MobileSellerOrderActionResponse toAssembling(@AuthenticationPrincipal Jwt jwt,
                                                        @PathVariable Long orderId) {
        return mobileSellerOrderService.toAssembling(jwt, orderId);
    }

    @PostMapping("/{orderId}/ready")
    public MobileSellerOrderActionResponse toReady(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable Long orderId) {
        return mobileSellerOrderService.toReady(jwt, orderId);
    }

    @PostMapping("/{orderId}/issue")
    public MobileSellerOrderActionResponse issue(@AuthenticationPrincipal Jwt jwt,
                                                 @PathVariable Long orderId,
                                                 @RequestBody MobileSellerIssueOrderRequest request) {
        return mobileSellerOrderService.issue(jwt, orderId, request);
    }

    @PostMapping("/{orderId}/cancel")
    public MobileSellerOrderActionResponse cancel(@AuthenticationPrincipal Jwt jwt,
                                                  @PathVariable Long orderId) {
        return mobileSellerOrderService.cancel(jwt, orderId);
    }
}
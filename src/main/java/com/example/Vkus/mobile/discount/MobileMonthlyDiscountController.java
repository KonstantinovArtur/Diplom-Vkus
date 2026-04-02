package com.example.Vkus.mobile.discount;

import com.example.Vkus.mobile.discount.dto.MobileChooseMonthlyDiscountRequest;
import com.example.Vkus.mobile.discount.dto.MobileChooseMonthlyDiscountResponse;
import com.example.Vkus.mobile.discount.dto.MobileMonthlyDiscountResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mobile/monthly-discount")
public class MobileMonthlyDiscountController {

    private final MobileMonthlyDiscountService mobileMonthlyDiscountService;

    public MobileMonthlyDiscountController(MobileMonthlyDiscountService mobileMonthlyDiscountService) {
        this.mobileMonthlyDiscountService = mobileMonthlyDiscountService;
    }

    @GetMapping("/categories")
    public MobileMonthlyDiscountResponse getCategories(@AuthenticationPrincipal Jwt jwt) {
        return mobileMonthlyDiscountService.getCategories(jwt);
    }

    @PostMapping("/choose")
    public MobileChooseMonthlyDiscountResponse choose(@AuthenticationPrincipal Jwt jwt,
                                                      @Valid @RequestBody MobileChooseMonthlyDiscountRequest request) {
        return mobileMonthlyDiscountService.chooseCategory(jwt, request.offerItemId());
    }
}
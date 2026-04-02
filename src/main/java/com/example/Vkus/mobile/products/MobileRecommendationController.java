package com.example.Vkus.mobile.products;

import com.example.Vkus.mobile.products.dto.MobileRecommendedProductDto;
import com.example.Vkus.service.BuyerRecommendationService;
import com.example.Vkus.web.dto.BuyerRecommendationVm;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/mobile/products")
public class MobileRecommendationController {

    private final BuyerRecommendationService recommendationService;

    public MobileRecommendationController(BuyerRecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{productId}/recommendations")
    public List<MobileRecommendedProductDto> recommendations(@AuthenticationPrincipal Jwt jwt,
                                                             @PathVariable Long productId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        List<BuyerRecommendationVm> items =
                recommendationService.getRecommendations(userId, buffetId, productId);

        return items.stream()
                .map(it -> new MobileRecommendedProductDto(
                        it.productId(),
                        it.name(),
                        it.description(),
                        it.categoryName(),
                        it.basePrice(),
                        it.finalPrice(),
                        it.promoPercent(),
                        it.promoText(),
                        it.monthlyPercent(),
                        it.monthlyText(),
                        it.batchPercent(),
                        it.batchText(),
                        it.hasImage()
                ))
                .toList();
    }

    private Long extractLong(Object value) {
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s) return Long.parseLong(s);

        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Некорректный uid в токене");
    }

    private Long extractOptionalLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Integer i) return i.longValue();
        if (value instanceof Number n) return n.longValue();
        if (value instanceof String s && !s.isBlank()) return Long.parseLong(s);
        return null;
    }
}
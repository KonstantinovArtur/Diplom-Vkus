package com.example.Vkus.web.buyer;

import com.example.Vkus.security.CurrentUserFacade;
import com.example.Vkus.service.BuyerRecommendationService;
import com.example.Vkus.web.dto.BuyerRecommendationVm;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/buyer/recommendations")
public class BuyerRecommendationController {

    private final CurrentUserFacade currentUser;
    private final BuyerRecommendationService recommendationService;

    public BuyerRecommendationController(CurrentUserFacade currentUser,
                                         BuyerRecommendationService recommendationService) {
        this.currentUser = currentUser;
        this.recommendationService = recommendationService;
    }

    @GetMapping("/{productId}")
    public List<BuyerRecommendationVm> getRecommendations(@PathVariable Long productId) {
        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        return recommendationService.getRecommendations(userId, buffetId, productId);
    }
}
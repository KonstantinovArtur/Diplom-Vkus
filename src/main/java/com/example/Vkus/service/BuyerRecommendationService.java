package com.example.Vkus.service;

import com.example.Vkus.entity.InventoryItem;
import com.example.Vkus.entity.Product;
import com.example.Vkus.entity.ProductRecommendation;
import com.example.Vkus.repository.InventoryItemRepository;
import com.example.Vkus.repository.ProductRecommendationRepository;
import com.example.Vkus.web.dto.BuyerRecommendationVm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class BuyerRecommendationService {

    private final ProductRecommendationRepository recommendationRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final BuyerPricingService pricingService;

    public BuyerRecommendationService(ProductRecommendationRepository recommendationRepository,
                                      InventoryItemRepository inventoryItemRepository,
                                      BuyerPricingService pricingService) {
        this.recommendationRepository = recommendationRepository;
        this.inventoryItemRepository = inventoryItemRepository;
        this.pricingService = pricingService;
    }

    @Transactional(readOnly = true)
    public List<BuyerRecommendationVm> getRecommendations(Long userId, Long buffetId, Long productId) {
        List<ProductRecommendation> recs =
                recommendationRepository.findActiveForProduct(buffetId, productId);

        if (recs.isEmpty()) {
            return List.of();
        }

        List<Product> products = new ArrayList<>();

        for (ProductRecommendation r : recs) {
            Product p = r.getRecommendedProduct();

            InventoryItem ii = inventoryItemRepository
                    .findByBuffet_IdAndProduct_Id(buffetId, p.getId())
                    .orElse(null);

            if (ii == null) continue;
            if (ii.getQuantity() == null || ii.getQuantity() <= 0) continue;
            if (!Boolean.TRUE.equals(p.getIsActive())) continue;

            products.add(p);
        }

        if (products.isEmpty()) {
            return List.of();
        }

        Map<Long, BuyerPricingService.Discounts> discounts =
                pricingService.resolveDiscounts(userId, buffetId, products);

        List<BuyerRecommendationVm> out = new ArrayList<>();

        for (Product p : products) {
            BuyerPricingService.Discounts d = discounts.get(p.getId());

            BigDecimal promo = d != null ? d.promoPercent() : null;
            BigDecimal monthly = d != null ? d.monthlyPercent() : null;
            BigDecimal batch = d != null ? d.batchPercent() : null;

            BigDecimal base = p.getBasePrice();
            BigDecimal finalPrice = pricingService.applyThreeDiscounts(base, batch, promo, monthly);

            String promoText = promo != null ? BuyerPricingService.asLabel("Акция", promo) : null;
            String monthlyText = monthly != null ? BuyerPricingService.asLabel("Скидка месяца", monthly) : null;
            String batchText = batch != null ? BuyerPricingService.asLabel("Уценка", batch) : null;

            out.add(new BuyerRecommendationVm(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getCategory() != null ? p.getCategory().getName() : "—",

                    base,
                    finalPrice,

                    promo,
                    promoText,

                    monthly,
                    monthlyText,

                    batch,
                    batchText,

                    p.getImageData() != null && p.getImageData().length > 0
            ));
        }

        return out;
    }
}
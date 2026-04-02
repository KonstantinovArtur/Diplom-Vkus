package com.example.Vkus.service;

import com.example.Vkus.entity.Product;
import com.example.Vkus.entity.ProductRecommendation;
import com.example.Vkus.entity.User;
import com.example.Vkus.repository.InventoryItemRepository;
import com.example.Vkus.repository.ProductRecommendationRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BuffetAdminRecommendationService {

    private final ProductRecommendationRepository recommendations;
    private final InventoryItemRepository inventory;
    private final ProductRepository products;
    private final UserRepository users;

    public BuffetAdminRecommendationService(ProductRecommendationRepository recommendations,
                                            InventoryItemRepository inventory,
                                            ProductRepository products,
                                            UserRepository users) {
        this.recommendations = recommendations;
        this.inventory = inventory;
        this.products = products;
        this.users = users;
    }

    public record CurrentCtx(Long userId, Long buffetId) {}

    public CurrentCtx requireCtx(Authentication auth) {
        if (auth == null || !(auth.getPrincipal() instanceof OidcUser oidc)) {
            throw new IllegalStateException("Нет авторизации");
        }

        String email = oidc.getEmail();
        User u = users.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException("Пользователь не найден в БД: " + email));

        if (u.getDefaultBuffetId() == null) {
            throw new IllegalStateException("У пользователя не задан default_buffet_id");
        }

        return new CurrentCtx(u.getId(), u.getDefaultBuffetId());
    }

    public List<Product> productsInBuffet(Long buffetId) {
        return inventory.findProductsInBuffet(buffetId);
    }

    public List<Product> candidateProducts(Long buffetId, Long productId) {
        return productsInBuffet(buffetId).stream()
                .filter(p -> !p.getId().equals(productId))
                .toList();
    }

    public List<ProductRecommendation> list(Long buffetId, Long productId) {
        ensureProductInBuffet(buffetId, productId);
        return recommendations.findAllForProduct(buffetId, productId);
    }

    @Transactional
    public void add(Long buffetId,
                    Long actorUserId,
                    Long productId,
                    Long recommendedProductId,
                    Integer sortOrder) {

        if (productId == null || recommendedProductId == null) {
            throw new IllegalArgumentException("Не выбран товар");
        }
        if (productId.equals(recommendedProductId)) {
            throw new IllegalArgumentException("Нельзя рекомендовать товар самому себе");
        }

        ensureProductInBuffet(buffetId, productId);
        ensureProductInBuffet(buffetId, recommendedProductId);

        if (recommendations.existsByBuffetIdAndProduct_IdAndRecommendedProduct_Id(
                buffetId, productId, recommendedProductId)) {
            throw new IllegalArgumentException("Такая рекомендация уже существует");
        }

        Product product = products.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Исходный товар не найден"));

        Product recommended = products.findById(recommendedProductId)
                .orElseThrow(() -> new IllegalArgumentException("Рекомендуемый товар не найден"));

        ProductRecommendation r = new ProductRecommendation();
        r.setBuffetId(buffetId);
        r.setProduct(product);
        r.setRecommendedProduct(recommended);
        r.setSortOrder(sortOrder == null ? 0 : sortOrder);
        r.setIsActive(true);
        r.setCreatedBy(actorUserId);

        recommendations.save(r);
    }

    @Transactional
    public void updateSort(Long id, Long buffetId, Integer sortOrder) {
        ProductRecommendation r = requireOwned(id, buffetId);
        r.setSortOrder(sortOrder == null ? 0 : sortOrder);
        recommendations.save(r);
    }

    @Transactional
    public void toggle(Long id, Long buffetId) {
        ProductRecommendation r = requireOwned(id, buffetId);
        r.setIsActive(!Boolean.TRUE.equals(r.getIsActive()));
        recommendations.save(r);
    }

    @Transactional
    public void delete(Long id, Long buffetId) {
        ProductRecommendation r = requireOwned(id, buffetId);
        recommendations.delete(r);
    }

    public ProductRecommendation requireOwned(Long id, Long buffetId) {
        ProductRecommendation r = recommendations.findByIdAndBuffetId(id, buffetId)
                .orElseThrow(() -> new IllegalArgumentException("Рекомендация не найдена"));
        if (!buffetId.equals(r.getBuffetId())) {
            throw new SecurityException("Чужой буфет");
        }
        return r;
    }

    private void ensureProductInBuffet(Long buffetId, Long productId) {
        if (!inventory.existsByBuffet_IdAndProduct_Id(buffetId, productId)) {
            throw new IllegalArgumentException("Товар не относится к текущему буфету");
        }
    }
}
package com.example.Vkus.service;

import com.example.Vkus.entity.Product;
import com.example.Vkus.entity.ProductDiscount;
import com.example.Vkus.entity.User;
import com.example.Vkus.repository.InventoryItemRepository;
import com.example.Vkus.repository.ProductDiscountRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.repository.UserRepository;
import com.example.Vkus.web.dto.ProductDiscountForm;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.BindingResult;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BuffetAdminDiscountService {

    private final ProductDiscountRepository discounts;
    private final InventoryItemRepository inventory;
    private final ProductRepository products;
    private final UserRepository users;

    public BuffetAdminDiscountService(ProductDiscountRepository discounts,
                                      InventoryItemRepository inventory,
                                      ProductRepository products,
                                      UserRepository users) {
        this.discounts = discounts;
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

    public List<ProductDiscount> list(Long buffetId) {
        return discounts.findByBuffetIdOrderByIsActiveDescStartAtDescCreatedAtDesc(buffetId);
    }

    public List<Product> productsInBuffet(Long buffetId) {
        return inventory.findProductsInBuffet(buffetId);
    }

    public ProductDiscountForm toForm(ProductDiscount d) {
        ProductDiscountForm f = new ProductDiscountForm();
        f.setId(d.getId());
        f.setProductId(d.getProduct().getId());
        f.setPercent(d.getPercent());
        f.setStartAt(d.getStartAt());
        f.setEndAt(d.getEndAt());
        f.setIsActive(Boolean.TRUE.equals(d.getIsActive()));
        return f;
    }

    public ProductDiscount requireOwned(Long id, Long buffetId) {
        ProductDiscount d = discounts.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Скидка не найдена"));
        if (!buffetId.equals(d.getBuffetId())) {
            throw new SecurityException("Чужой буфет");
        }
        return d;
    }

    @Transactional
    public void save(ProductDiscountForm form, BindingResult br, Long buffetId, Long actorUserId) {
        // бизнес-валидация дат
        if (form.getEndAt() != null && form.getStartAt() != null && form.getEndAt().isBefore(form.getStartAt())) {
            br.reject("endBeforeStart", "Дата окончания не может быть раньше даты начала");
        }
        LocalDateTime now = LocalDateTime.now();

        if (form.getStartAt() != null && form.getStartAt().isBefore(now)) {
            br.reject("startInPast", "Дата начала скидки не может быть в прошлом");
        }

        // товар должен быть из ассортимента буфета
        boolean productAllowed = productsInBuffet(buffetId).stream()
                .anyMatch(p -> p.getId().equals(form.getProductId()));
        if (!productAllowed) {
            br.reject("badProduct", "Выбранный товар не относится к текущему буфету (нет в inventory_items)");
        }

        // уникальность: только 1 активная скидка на товар (есть частичный индекс, но дадим нормальную ошибку)
        if (Boolean.TRUE.equals(form.getIsActive())) {
            discounts.findAnotherActive(buffetId, form.getProductId(), form.getId())
                    .ifPresent(x -> br.reject("alreadyActive",
                            "У этого товара уже есть активная скидка. Сначала отключите её."));
        }

        if (br.hasErrors()) return;

        Product p = products.findById(form.getProductId())
                .orElseThrow(() -> new IllegalArgumentException("Товар не найден"));

        ProductDiscount d;
        if (form.getId() == null) {
            d = new ProductDiscount();
            d.setBuffetId(buffetId);
            d.setCreatedBy(actorUserId);
        } else {
            d = requireOwned(form.getId(), buffetId);
        }

        d.setProduct(p);
        d.setPercent(form.getPercent());
        d.setStartAt(form.getStartAt());
        d.setEndAt(form.getEndAt());
        d.setIsActive(form.getIsActive());

        discounts.save(d);
    }

    @Transactional
    public void deactivate(Long id, Long buffetId) {
        ProductDiscount d = requireOwned(id, buffetId);
        d.setIsActive(false);
        discounts.save(d);
    }

    @Transactional
    public void deleteHard(Long id, Long buffetId) {
        ProductDiscount d = requireOwned(id, buffetId);
        discounts.delete(d);
    }
}

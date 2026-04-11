package com.example.Vkus.mobile.cart;

import com.example.Vkus.entity.Cart;
import com.example.Vkus.entity.CartItem;
import com.example.Vkus.mobile.cart.dto.*;
import com.example.Vkus.repository.CartItemRepository;
import com.example.Vkus.repository.CartRepository;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.BuyerPricingService;
import com.example.Vkus.service.CartComboService;
import com.example.Vkus.service.CartService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Service
public class MobileCartService {

    private final CartService cartService;
    private final CartComboService cartComboService;
    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final BuyerPricingService pricingService;
    private final AuditLogService audit;

    public MobileCartService(CartService cartService,
                             CartComboService cartComboService,
                             CartRepository cartRepository,
                             CartItemRepository cartItemRepository,
                             BuyerPricingService pricingService,
                             AuditLogService audit) {
        this.cartService = cartService;
        this.cartComboService = cartComboService;
        this.cartRepository = cartRepository;
        this.cartItemRepository = cartItemRepository;
        this.pricingService = pricingService;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public MobileCartResponse getCart(Jwt jwt) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseGet(() -> {
                    Cart c = new Cart();
                    c.setUserId(userId);
                    c.setBuffetId(buffetId);
                    return cartRepository.save(c);
                });

        List<CartItem> items = cartItemRepository.findAllByCartIdWithProduct(cart.getId());

        var products = items.stream()
                .map(CartItem::getProduct)
                .toList();

        Map<Long, BuyerPricingService.Discounts> discounts =
                pricingService.resolveDiscounts(userId, buffetId, products);

        List<MobileCartItemDto> dtoItems = items.stream().map(ci -> {
            var p = ci.getProduct();
            var d = discounts.get(p.getId());

            BigDecimal promo = d != null ? d.promoPercent() : null;
            BigDecimal monthly = d != null ? d.monthlyPercent() : null;
            BigDecimal batch = d != null ? d.batchPercent() : null;

            BigDecimal unitPrice = pricingService.applyThreeDiscounts(
                    p.getBasePrice(),
                    batch,
                    promo,
                    monthly
            );

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(ci.getQty()));

            String imageUrl = (p.getImageData() != null && p.getImageData().length > 0)
                    ? "/products/" + p.getId() + "/image"
                    : null;

            return new MobileCartItemDto(
                    ci.getId(),
                    p.getId(),
                    p.getName(),
                    imageUrl,
                    unitPrice,
                    ci.getQty(),
                    lineTotal
            );
        }).toList();

        var combos = cartComboService.getCombos(userId, buffetId);

        List<MobileCartComboDto> comboItems = combos.stream().map(cc -> new MobileCartComboDto(
                cc.getId(),
                cc.getComboTemplate().getId(),
                cc.getComboTemplate().getName(),
                cc.getComboPriceSnapshot(),
                cc.getQty(),
                cc.getComboPriceSnapshot().multiply(BigDecimal.valueOf(cc.getQty())),
                cc.getItems().stream()
                        .map(it -> new MobileCartComboSelectionDto(
                                it.getComboSlot().getName(),
                                it.getProduct().getName(),
                                it.getExtraPriceSnapshot()
                        ))
                        .toList()
        )).toList();

        int totalProductItems = dtoItems.stream()
                .mapToInt(MobileCartItemDto::qty)
                .sum();

        int totalComboItems = comboItems.stream()
                .mapToInt(MobileCartComboDto::qty)
                .sum();

        BigDecimal totalProductsAmount = dtoItems.stream()
                .map(MobileCartItemDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCombosAmount = comboItems.stream()
                .map(MobileCartComboDto::lineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new MobileCartResponse(
                dtoItems,
                comboItems,
                totalProductItems + totalComboItems,
                totalProductsAmount.add(totalCombosAmount)
        );
    }

    @Transactional
    public MobileCartActionResponse addToCart(Jwt jwt, Long productId, Integer qty) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        int safeQty = (qty == null || qty <= 0) ? 1 : qty;

        try {
            cartService.add(userId, buffetId, productId, safeQty);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        audit.log("MOBILE_CART_ADD_PRODUCT", "product", productId, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId,
                "qty", safeQty
        ));

        return new MobileCartActionResponse(true, "Товар добавлен в корзину");
    }

    @Transactional
    public MobileCartActionResponse updateQty(Jwt jwt, Long cartItemId, Integer qty) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        int safeQty = (qty == null || qty <= 0) ? 1 : qty;

        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Корзина не найдена"));

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Позиция корзины не найдена"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Чужая позиция корзины");
        }

        try {
            cartService.setQty(userId, buffetId, item.getProduct().getId(), safeQty);
        } catch (IllegalStateException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        }

        return new MobileCartActionResponse(true, "Количество обновлено");
    }

    @Transactional
    public MobileCartActionResponse removeItem(Jwt jwt, Long cartItemId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        Cart cart = cartRepository.findByUserIdAndBuffetId(userId, buffetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Корзина не найдена"));

        CartItem item = cartItemRepository.findById(cartItemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Позиция корзины не найдена"));

        if (!item.getCart().getId().equals(cart.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Чужая позиция корзины");
        }

        cartItemRepository.delete(item);

        return new MobileCartActionResponse(true, "Товар удалён из корзины");
    }

    @Transactional
    public MobileCartActionResponse removeCombo(Jwt jwt, Long cartComboId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        cartComboService.removeCombo(userId, buffetId, cartComboId);

        audit.log("MOBILE_CART_REMOVE_COMBO", "cart_combo", cartComboId, Map.of(
                "actorUserId", userId,
                "buffetId", buffetId
        ));

        return new MobileCartActionResponse(true, "Комбо удалено из корзины");
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
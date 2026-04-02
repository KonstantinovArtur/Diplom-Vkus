package com.example.Vkus.mobile.menu;

import com.example.Vkus.entity.Category;
import com.example.Vkus.entity.Product;
import com.example.Vkus.mobile.menu.dto.MobileMenuCategoryDto;
import com.example.Vkus.mobile.menu.dto.MobileMenuItemDto;
import com.example.Vkus.mobile.menu.dto.MobileMenuResponse;
import com.example.Vkus.repository.CategoryRepository;
import com.example.Vkus.service.BuyerCatalogService;
import com.example.Vkus.service.BuyerPricingService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class MobileMenuService {

    private final BuyerCatalogService catalogService;
    private final BuyerPricingService pricingService;
    private final CategoryRepository categoryRepository;
    private final JdbcTemplate jdbc;

    private record ExpiryDiscountInfo(int qty, BigDecimal percent) {}

    public MobileMenuService(BuyerCatalogService catalogService,
                             BuyerPricingService pricingService,
                             CategoryRepository categoryRepository,
                             JdbcTemplate jdbc) {
        this.catalogService = catalogService;
        this.pricingService = pricingService;
        this.categoryRepository = categoryRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public MobileMenuResponse getMenu(Jwt jwt, String q, Long categoryId) {
        Long userId = extractLong(jwt.getClaims().get("uid"));
        Long buffetId = extractOptionalLong(jwt.getClaims().get("defaultBuffetId"));

        if (buffetId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "У пользователя не выбран буфет");
        }

        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByNameAsc();
        List<Product> products = catalogService.findProducts(categoryId, q);

        Map<Long, Long> stockQtyByProductId = loadStockQtyByProduct(buffetId, products);
        Map<Long, BuyerPricingService.Discounts> discounts =
                pricingService.resolveDiscounts(userId, buffetId, products);
        Map<Long, ExpiryDiscountInfo> expiryInfoByProductId =
                loadExpiryDiscountInfoByProduct(buffetId, products);

        List<MobileMenuItemDto> items = products.stream().map(p -> {
            BuyerPricingService.Discounts d = discounts.get(p.getId());

            BigDecimal promo = d != null ? d.promoPercent() : null;
            BigDecimal monthly = d != null ? d.monthlyPercent() : null;
            BigDecimal batchForPrice = d != null ? d.batchPercent() : null;

            BigDecimal basePrice = p.getBasePrice();
            BigDecimal finalPrice = pricingService.applyThreeDiscounts(basePrice, batchForPrice, promo, monthly);

            ExpiryDiscountInfo info = expiryInfoByProductId.get(p.getId());

            BigDecimal batchPercentForBadge = null;
            String batchText = null;
            if (info != null && info.qty() > 0 && info.percent() != null) {
                batchPercentForBadge = info.percent();
                batchText = "Уценка " + stripZeros(info.percent()) + "% · " + info.qty() + " шт";
            }

            String promoText = promo != null ? BuyerPricingService.asLabel("Акция", promo) : null;
            String monthlyText = monthly != null ? BuyerPricingService.asLabel("Скидка месяца", monthly) : null;

            long stockQty = stockQtyByProductId.getOrDefault(p.getId(), 0L);
            boolean hasImage = p.getImageData() != null && p.getImageData().length > 0;

            return new MobileMenuItemDto(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getCategory() != null ? p.getCategory().getName() : "—",

                    basePrice,
                    finalPrice,

                    promo,
                    promoText,

                    monthly,
                    monthlyText,

                    batchPercentForBadge,
                    batchText,

                    stockQty,
                    stockQty > 0,

                    hasImage,
                    hasImage ? "/products/" + p.getId() + "/image" : null
            );
        }).toList();

        List<MobileMenuCategoryDto> categoryDtos = categories.stream()
                .map(c -> new MobileMenuCategoryDto(c.getId(), c.getName()))
                .toList();

        return new MobileMenuResponse(
                buffetId,
                normalizeQ(q),
                categoryId,
                categoryDtos,
                items
        );
    }

    private Map<Long, ExpiryDiscountInfo> loadExpiryDiscountInfoByProduct(Long buffetId, List<Product> products) {
        if (products == null || products.isEmpty()) return Map.of();

        List<Long> productIds = products.stream().map(Product::getId).distinct().toList();
        if (productIds.isEmpty()) return Map.of();

        String inSql = productIds.stream().map(x -> "?").collect(Collectors.joining(","));

        String sql = """
                SELECT b.product_id,
                       SUM(b.qty_available) AS qty,
                       MAX(bd.percent)      AS percent
                FROM inventory_batches b
                JOIN batch_discounts bd
                  ON bd.batch_id = b.id
                 AND bd.is_active = TRUE
                WHERE b.buffet_id = ?
                  AND b.status = 'active'
                  AND b.qty_available > 0
                  AND b.product_id IN (%s)
                GROUP BY b.product_id
                """.formatted(inSql);

        Object[] args = new Object[1 + productIds.size()];
        args[0] = buffetId;
        for (int i = 0; i < productIds.size(); i++) {
            args[i + 1] = productIds.get(i);
        }

        Map<Long, ExpiryDiscountInfo> map = new HashMap<>();
        jdbc.query(sql, args, (ResultSet rs) -> {
            long productId = rs.getLong("product_id");
            int qty = rs.getInt("qty");
            BigDecimal percent = rs.getBigDecimal("percent");
            map.put(productId, new ExpiryDiscountInfo(qty, percent));
        });

        return map;
    }

    private Map<Long, Long> loadStockQtyByProduct(Long buffetId, List<Product> products) {
        if (products == null || products.isEmpty()) return Map.of();

        List<Long> productIds = products.stream()
                .map(Product::getId)
                .distinct()
                .toList();

        if (productIds.isEmpty()) return Map.of();

        String inSql = productIds.stream()
                .map(x -> "?")
                .collect(Collectors.joining(","));

        String sql = """
                SELECT product_id, COALESCE(quantity, 0) AS qty
                FROM inventory_items
                WHERE buffet_id = ?
                  AND product_id IN (%s)
                """.formatted(inSql);

        Object[] args = new Object[1 + productIds.size()];
        args[0] = buffetId;
        for (int i = 0; i < productIds.size(); i++) {
            args[i + 1] = productIds.get(i);
        }

        Map<Long, Long> map = new HashMap<>();
        jdbc.query(sql, args, rs -> {
            map.put(rs.getLong("product_id"), rs.getLong("qty"));
        });

        return map;
    }

    private static String stripZeros(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }

    private static String normalizeQ(String q) {
        if (q == null) return null;
        String trimmed = q.trim();
        return trimmed.isBlank() ? null : trimmed;
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
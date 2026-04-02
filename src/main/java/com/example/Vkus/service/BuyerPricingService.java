package com.example.Vkus.service;

import com.example.Vkus.entity.*;
import com.example.Vkus.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class BuyerPricingService {

    private final ProductDiscountRepository productDiscountRepository;
    private final MonthlyDiscountOfferRepository monthlyDiscountOfferRepository;
    private final UserMonthlyCategoryChoiceRepository userMonthlyCategoryChoiceRepository;
    private final JdbcTemplate jdbc;

    // ✅ добавили batchPercent
    public record Discounts(BigDecimal promoPercent, BigDecimal monthlyPercent, BigDecimal batchPercent) {}

    public BuyerPricingService(ProductDiscountRepository productDiscountRepository,
                               MonthlyDiscountOfferRepository monthlyDiscountOfferRepository,
                               MonthlyDiscountOfferItemRepository monthlyDiscountOfferItemRepository, // можно оставить
                               UserMonthlyCategoryChoiceRepository userMonthlyCategoryChoiceRepository,
                               JdbcTemplate jdbc) {
        this.productDiscountRepository = productDiscountRepository;
        this.monthlyDiscountOfferRepository = monthlyDiscountOfferRepository;
        this.userMonthlyCategoryChoiceRepository = userMonthlyCategoryChoiceRepository;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public Map<Long, Discounts> resolveDiscounts(Long userId, Long buffetId, List<Product> products) {
        LocalDateTime now = LocalDateTime.now();

        // 1) Акции на конкретные товары
        Map<Long, BigDecimal> promoPct = new HashMap<>();
        for (ProductDiscount d : productDiscountRepository.findActiveForBuffet(buffetId, now)) {
            promoPct.put(d.getProduct().getId(), d.getPercent());
        }

        // 2) Скидка месяца по категории (выбор пользователя)
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        int month = today.getMonthValue();

        Optional<MonthlyDiscountOffer> offerOpt =
                monthlyDiscountOfferRepository.findByBuffetIdAndYearAndMonth(buffetId, year, month);

        Long chosenCategoryId = null;
        BigDecimal chosenPercent = null;

        if (offerOpt.isPresent()) {
            Optional<UserMonthlyCategoryChoice> choiceOpt =
                    userMonthlyCategoryChoiceRepository.findChoiceWithOfferItem(userId, buffetId, year, month);

            if (choiceOpt.isPresent()) {
                MonthlyDiscountOfferItem oi = choiceOpt.get().getOfferItem();
                chosenCategoryId = oi.getCategory().getId();
                chosenPercent = oi.getPercent();
            }
        }

        // 3) ✅ batch-скидка (по первой FEFO-партии, если она со скидкой)
        Map<Long, BigDecimal> batchPct = resolveBatchDiscountsForProducts(buffetId, products);

        Map<Long, Discounts> out = new HashMap<>();
        for (Product p : products) {
            BigDecimal promo = promoPct.get(p.getId());

            BigDecimal monthly = null;
            if (chosenCategoryId != null && chosenPercent != null) {
                Category c = p.getCategory();
                if (c != null && Objects.equals(c.getId(), chosenCategoryId)) {
                    monthly = chosenPercent;
                }
            }

            BigDecimal batch = batchPct.get(p.getId());

            if (promo != null || monthly != null || batch != null) {
                out.put(p.getId(), new Discounts(promo, monthly, batch));
            }
        }
        return out;
    }

    /**
     * ✅ Берём скидку партии по "первой" (FEFO) активной партии с остатком,
     * у которой есть активная запись batch_discounts.
     */
    private Map<Long, BigDecimal> resolveBatchDiscountsForProducts(Long buffetId, List<Product> products) {
        if (products == null || products.isEmpty()) return Map.of();

        List<Long> ids = products.stream().map(Product::getId).distinct().toList();
        String inSql = String.join(",", ids.stream().map(x -> "?").toList());

        String sql = """
                SELECT DISTINCT ON (b.product_id)
                       b.product_id,
                       bd.percent
                FROM inventory_batches b
                JOIN batch_discounts bd ON bd.batch_id = b.id AND bd.is_active = TRUE
                WHERE b.buffet_id = ?
                  AND b.status = 'active'
                  AND b.qty_available > 0
                  AND b.product_id IN (%s)
                ORDER BY b.product_id,
                         (b.expires_at IS NULL), b.expires_at, b.received_at, b.id
                """.formatted(inSql);

        Object[] args = new Object[ids.size() + 1];
        args[0] = buffetId;
        for (int i = 0; i < ids.size(); i++) args[i + 1] = ids.get(i);

        Map<Long, BigDecimal> map = new HashMap<>();
        jdbc.query(sql, rs -> {
            map.put(rs.getLong("product_id"), rs.getBigDecimal("percent"));
        }, args);

        return map;
    }

    public BigDecimal applyThreeDiscounts(BigDecimal basePrice,
                                          BigDecimal batchPercent,
                                          BigDecimal promoPercent,
                                          BigDecimal monthlyPercent) {
        BigDecimal price = (basePrice == null ? BigDecimal.ZERO : basePrice).setScale(2, RoundingMode.HALF_UP);

        // ✅ скидки суммируются (применяются последовательно)
        price = applyDiscount(price, batchPercent);
        price = applyDiscount(price, promoPercent);
        price = applyDiscount(price, monthlyPercent);

        return price.setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal applyDiscount(BigDecimal price, BigDecimal percent) {
        if (price == null) return BigDecimal.ZERO;
        if (percent == null || percent.signum() <= 0) return price.setScale(2, RoundingMode.HALF_UP);

        BigDecimal multiplier = BigDecimal.ONE.subtract(percent.movePointLeft(2));
        return price.multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
    }

    // оставим, чтобы старый код/шаблоны не падали
    public BigDecimal applyTwoDiscounts(BigDecimal basePrice, BigDecimal promoPercent, BigDecimal monthlyPercent) {
        return applyThreeDiscounts(basePrice, null, promoPercent, monthlyPercent);
    }

    public static String asLabel(String prefix, BigDecimal percent) {
        return prefix + " " + percent.stripTrailingZeros().toPlainString() + "%";
    }
}
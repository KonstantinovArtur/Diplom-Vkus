package com.example.Vkus.web.buyer;

import com.example.Vkus.entity.Category;
import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.CategoryRepository;
import com.example.Vkus.security.CurrentUserFacade;
import com.example.Vkus.service.BuyerCatalogService;
import com.example.Vkus.service.BuyerPricingService;
import com.example.Vkus.web.dto.MenuItemVm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/menu")
public class BuyerMenuController {

    private final BuyerCatalogService catalogService;
    private final BuyerPricingService pricingService;
    private final CategoryRepository categoryRepository;
    private final CurrentUserFacade currentUser;
    private final JdbcTemplate jdbc;

    // product_id -> (qty, percent)
    private record ExpiryDiscountInfo(int qty, BigDecimal percent) {}

    public BuyerMenuController(BuyerCatalogService catalogService,
                               BuyerPricingService pricingService,
                               CategoryRepository categoryRepository,
                               CurrentUserFacade currentUser,
                               JdbcTemplate jdbc) {
        this.catalogService = catalogService;
        this.pricingService = pricingService;
        this.categoryRepository = categoryRepository;
        this.currentUser = currentUser;
        this.jdbc = jdbc;
    }

    @GetMapping
    public String menu(@RequestParam(value = "q", required = false) String q,
                       @RequestParam(value = "categoryId", required = false) Long categoryId,
                       Model model) {

        Long userId = currentUser.requireUserId();
        Long buffetId = currentUser.requireBuffetId();

        List<Category> categories = categoryRepository.findByIsActiveTrueOrderByNameAsc();
        List<Product> products = catalogService.findProducts(categoryId, q);
        Map<Long, Long> stockQtyByProductId = loadStockQtyByProduct(buffetId, products);
        // скидки как у тебя сейчас (ничего не ломаем)
        Map<Long, BuyerPricingService.Discounts> discounts =
                pricingService.resolveDiscounts(userId, buffetId, products);

        // ✅ НОВОЕ: по уценке (batch_discounts) берём сразу qty + percent
        Map<Long, ExpiryDiscountInfo> expiryInfoByProductId =
                loadExpiryDiscountInfoByProduct(buffetId, products);

        List<MenuItemVm> items = products.stream().map(p -> {
            BuyerPricingService.Discounts d = discounts.get(p.getId());

            BigDecimal promo = (d != null) ? d.promoPercent() : null;
            BigDecimal monthly = (d != null) ? d.monthlyPercent() : null;

            // ❗ batchPercent для цены оставляем как было (из pricingService),
            // чтобы не ломать текущий расчёт
            BigDecimal batchForPrice = (d != null) ? d.batchPercent() : null;

            BigDecimal base = p.getBasePrice();
            BigDecimal finalPrice = pricingService.applyThreeDiscounts(base, batchForPrice, promo, monthly);

            // ✅ для бейджа в меню используем проценты/остаток из БД, а не из pricingService
            ExpiryDiscountInfo info = expiryInfoByProductId.get(p.getId());


            BigDecimal batchPercentForBadge = null;
            String batchText = null;
            if (info != null && info.qty() > 0 && info.percent() != null) {
                batchPercentForBadge = info.percent();

                // красивый текст без хвостов .00
                String percentStr = stripZeros(info.percent());
                batchText = "Бонус " + percentStr + "% · " + info.qty() + " шт";
            }

            String promoText = (promo != null) ? BuyerPricingService.asLabel("Акция", promo) : null;
            String monthlyText = (monthly != null) ? BuyerPricingService.asLabel("Скидка месяца", monthly) : null;

            return new MenuItemVm(
                    p.getId(),
                    p.getName(),
                    p.getDescription(),
                    p.getCategory() != null ? p.getCategory().getName() : "—",
                    base,
                    finalPrice,

                    // ✅ в карточку отдаём batchPercent + batchText для отображения
                    batchPercentForBadge,
                    batchText,

                    promo,
                    promoText,

                    monthly,
                    monthlyText,

                    p.getImageData() != null && p.getImageData().length > 0
            );
        }).toList();

        model.addAttribute("categories", categories);
        model.addAttribute("items", items);
        model.addAttribute("q", q);
        model.addAttribute("categoryId", categoryId);
        model.addAttribute("stockQtyByProductId", stockQtyByProductId);

        return "buyer/menu";
    }

    /**
     * product_id -> сколько штук осталось в активных партиях, на которые есть активная уценка,
     * и какой процент уценки показывать (берём MAX(percent)).
     */
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
            map.put(
                    rs.getLong("product_id"),
                    rs.getLong("qty")
            );
        });

        return map;
    }

    private static String stripZeros(BigDecimal v) {
        if (v == null) return "";
        return v.stripTrailingZeros().toPlainString();
    }
}
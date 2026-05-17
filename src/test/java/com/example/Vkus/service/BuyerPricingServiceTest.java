package com.example.Vkus.service;

import com.example.Vkus.repository.MonthlyDiscountOfferItemRepository;
import com.example.Vkus.repository.MonthlyDiscountOfferRepository;
import com.example.Vkus.repository.ProductDiscountRepository;
import com.example.Vkus.repository.UserMonthlyCategoryChoiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class BuyerPricingServiceTest {

    private BuyerPricingService service;

    @BeforeEach
    void setUp() {
        ProductDiscountRepository productDiscountRepository = mock(ProductDiscountRepository.class);
        MonthlyDiscountOfferRepository monthlyDiscountOfferRepository = mock(MonthlyDiscountOfferRepository.class);
        MonthlyDiscountOfferItemRepository monthlyDiscountOfferItemRepository = mock(MonthlyDiscountOfferItemRepository.class);
        UserMonthlyCategoryChoiceRepository userMonthlyCategoryChoiceRepository = mock(UserMonthlyCategoryChoiceRepository.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);

        service = new BuyerPricingService(
                productDiscountRepository,
                monthlyDiscountOfferRepository,
                monthlyDiscountOfferItemRepository,
                userMonthlyCategoryChoiceRepository,
                jdbc
        );
    }

    @Test
    void applyDiscount_whenPercentIsNull_returnsOriginalPriceWithTwoDigits() {
        BigDecimal result = service.applyDiscount(
                new BigDecimal("120.5"),
                null
        );

        assertEquals(new BigDecimal("120.50"), result);
    }

    @Test
    void applyDiscount_whenPercentIsPositive_appliesDiscount() {
        BigDecimal result = service.applyDiscount(
                new BigDecimal("100.00"),
                new BigDecimal("10")
        );

        assertEquals(new BigDecimal("90.00"), result);
    }

    @Test
    void applyThreeDiscounts_appliesBatchPromoAndMonthlySequentially() {
        BigDecimal result = service.applyThreeDiscounts(
                new BigDecimal("100.00"),
                new BigDecimal("10"),
                new BigDecimal("20"),
                new BigDecimal("5")
        );

        assertEquals(new BigDecimal("68.40"), result);
    }
}
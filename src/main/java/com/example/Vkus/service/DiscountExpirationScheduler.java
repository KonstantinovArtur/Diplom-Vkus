package com.example.Vkus.service;

import com.example.Vkus.repository.ProductDiscountRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
public class DiscountExpirationScheduler {

    private final ProductDiscountRepository discounts;

    public DiscountExpirationScheduler(ProductDiscountRepository discounts) {
        this.discounts = discounts;
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void deactivateExpiredDiscounts() {
        int n = discounts.deactivateExpired(LocalDateTime.now());
        System.out.println("[DiscountExpirationScheduler] deactivated = " + n);
    }

}

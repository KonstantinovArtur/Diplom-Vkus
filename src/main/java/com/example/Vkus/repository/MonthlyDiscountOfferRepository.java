package com.example.Vkus.repository;

import com.example.Vkus.entity.MonthlyDiscountOffer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonthlyDiscountOfferRepository extends JpaRepository<MonthlyDiscountOffer, Long> {

    Optional<MonthlyDiscountOffer> findByBuffetIdAndYearAndMonth(Long buffetId, int year, int month);

    // последний оффер по времени (на практике по year/month)
    Optional<MonthlyDiscountOffer> findFirstByBuffetIdOrderByYearDescMonthDesc(Long buffetId);
    Optional<MonthlyDiscountOffer> findFirstByBuffetIdAndIdNotOrderByYearDescMonthDesc(Long buffetId, Long id);

}

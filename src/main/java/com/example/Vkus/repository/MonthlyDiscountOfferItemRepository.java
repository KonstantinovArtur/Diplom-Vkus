package com.example.Vkus.repository;

import com.example.Vkus.entity.MonthlyDiscountOfferItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MonthlyDiscountOfferItemRepository extends JpaRepository<MonthlyDiscountOfferItem, Long> {
    List<MonthlyDiscountOfferItem> findByOfferIdOrderByIdAsc(Long offerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from MonthlyDiscountOfferItem i where i.offer.id = :offerId")
    void deleteByOfferId(@Param("offerId") Long offerId);

}

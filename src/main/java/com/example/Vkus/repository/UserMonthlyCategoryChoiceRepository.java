package com.example.Vkus.repository;

import com.example.Vkus.entity.UserMonthlyCategoryChoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface UserMonthlyCategoryChoiceRepository extends JpaRepository<UserMonthlyCategoryChoice, Long> {

    @Query("""
      select ch
      from UserMonthlyCategoryChoice ch
      join fetch ch.offerItem oi
      join fetch oi.category c
      join fetch oi.offer o
      where ch.userId = :userId
        and ch.buffetId = :buffetId
        and ch.year = :year
        and ch.month = :month
    """)
    Optional<UserMonthlyCategoryChoice> findChoiceWithOfferItem(Long userId, Long buffetId, int year, int month);
    Optional<UserMonthlyCategoryChoice> findByUserIdAndBuffetIdAndYearAndMonth(
            Long userId, Long buffetId, int year, int month
    );
    boolean existsByBuffetIdAndYearAndMonth(Long buffetId, Integer year, Integer month);

}

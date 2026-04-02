package com.example.Vkus.repository;

import com.example.Vkus.entity.ProductDiscount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ProductDiscountRepository extends JpaRepository<ProductDiscount, Long> {

    List<ProductDiscount> findByBuffetIdOrderByIsActiveDescStartAtDescCreatedAtDesc(Long buffetId);

    @Query("""
        select pd from ProductDiscount pd
        where pd.buffetId = :buffetId
          and pd.product.id = :productId
          and pd.isActive = true
          and pd.startAt <= :now
          and (pd.endAt is null or pd.endAt >= :now)
    """)
    Optional<ProductDiscount> findActiveForPricing(Long buffetId, Long productId, LocalDateTime now);

    @Query("""
        select pd from ProductDiscount pd
        where pd.buffetId = :buffetId
          and pd.product.id = :productId
          and pd.isActive = true
          and (:excludeId is null or pd.id <> :excludeId)
    """)
    Optional<ProductDiscount> findAnotherActive(Long buffetId, Long productId, Long excludeId);

    @Query("""
      select d
      from ProductDiscount d
      join fetch d.product p
      where d.buffetId = :buffetId
        and d.isActive = true
        and d.startAt <= :now
        and (d.endAt is null or d.endAt >= :now)
    """)
    List<ProductDiscount> findActiveForBuffet(Long buffetId, LocalDateTime now);
    @Modifying
    @Query("""
    update ProductDiscount d
    set d.isActive = false
    where d.isActive = true
      and d.endAt is not null
      and d.endAt < :now
""")
    int deactivateExpired(LocalDateTime now);

}

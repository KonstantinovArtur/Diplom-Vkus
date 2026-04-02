package com.example.Vkus.repository;

import com.example.Vkus.entity.ProductRecommendation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRecommendationRepository extends JpaRepository<ProductRecommendation, Long> {

    @Query("""
        select r
        from ProductRecommendation r
        join fetch r.product p
        join fetch r.recommendedProduct rp
        join fetch rp.category c
        left join fetch c.parent cp
        where r.buffetId = :buffetId
          and p.id = :productId
        order by r.isActive desc, r.sortOrder asc, rp.name asc
    """)
    List<ProductRecommendation> findAllForProduct(@Param("buffetId") Long buffetId,
                                                  @Param("productId") Long productId);

    Optional<ProductRecommendation> findByIdAndBuffetId(Long id, Long buffetId);

    boolean existsByBuffetIdAndProduct_IdAndRecommendedProduct_Id(Long buffetId,
                                                                  Long productId,
                                                                  Long recommendedProductId);
    @Query("""
    select r
    from ProductRecommendation r
    join fetch r.recommendedProduct rp
    join fetch rp.category c
    left join fetch c.parent cp
    where r.buffetId = :buffetId
      and r.product.id = :productId
      and r.isActive = true
      and rp.isActive = true
    order by r.sortOrder asc, rp.name asc
""")
    List<ProductRecommendation> findActiveForProduct(@Param("buffetId") Long buffetId,
                                                     @Param("productId") Long productId);
}
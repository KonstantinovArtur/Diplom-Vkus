package com.example.Vkus.repository;

import com.example.Vkus.entity.Product;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SellerStockRepository extends JpaRepository<Product, Long> {

    @Query(value = """
        SELECT
            p.id          AS product_id,
            p.name        AS name,
            p.base_price  AS base_price,
            COALESCE(ii.quantity, 0) AS qty
        FROM products p
        LEFT JOIN inventory_items ii
               ON ii.product_id = p.id
              AND ii.buffet_id  = :buffetId
        WHERE p.is_active = TRUE
        ORDER BY p.name
        """, nativeQuery = true)
    List<Object[]> findStocksRaw(@Param("buffetId") Long buffetId);
}

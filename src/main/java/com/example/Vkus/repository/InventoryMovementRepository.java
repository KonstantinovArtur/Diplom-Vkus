package com.example.Vkus.repository;

import com.example.Vkus.entity.InventoryMovement;
import com.example.Vkus.web.dto.WriteoffProductRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {

    @Query(value = """
            select
                p.id as productId,
                p.name as productName,
                cast(coalesce(sum(abs(im.qty)), 0) as bigint) as writeoffQty
            from inventory_movements im
            join products p on p.id = im.product_id
            where im.buffet_id = :buffetId
              and im.type = 'writeoff'
              and im.created_at >= cast(:from as timestamp)
              and im.created_at < (cast(:to as timestamp) + interval '1 day')
            group by p.id, p.name
            order by writeoffQty desc, p.name asc
            limit 10
            """, nativeQuery = true)
    List<WriteoffProductRow> findTopWriteoffs(@Param("buffetId") Long buffetId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);
}
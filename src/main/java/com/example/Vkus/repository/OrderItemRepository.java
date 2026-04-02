package com.example.Vkus.repository;

import com.example.Vkus.entity.OrderItem;
import com.example.Vkus.web.dto.TopProductRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    @Query("""
        select
          p.id as productId,
          p.name as productName,
          coalesce(sum(oi.qty), 0) as qty,
          coalesce(sum(oi.finalLineAmount), 0) as revenue
        from OrderItem oi
          join oi.order o
          join oi.product p
        where o.buffet.id = :buffetId
          and o.orderDate between :from and :to
          and o.status in :statuses
        group by p.id, p.name
        order by sum(oi.qty) desc, sum(oi.finalLineAmount) desc
    """)
    List<TopProductRow> findTopProducts(
            @Param("buffetId") Long buffetId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") List<String> statuses
    );

    List<OrderItem> findByOrderId(Long orderId);
}


package com.example.Vkus.repository;

import com.example.Vkus.entity.*;
import com.example.Vkus.web.dto.DailySalesRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("""
        select
          o.orderDate as day,
          count(o.id) as ordersCount,
          coalesce(sum(o.finalAmount), 0) as revenue
        from Order o
        where o.buffet.id = :buffetId
          and o.orderDate between :from and :to
          and o.status in :statuses
        group by o.orderDate
        order by o.orderDate
    """)
    List<DailySalesRow> findDailySales(
            @Param("buffetId") Long buffetId,
            @Param("from") LocalDate from,
            @Param("to") LocalDate to,
            @Param("statuses") List<String> statuses
    );

    List<Order> findByUserIdAndBuffetIdOrderByCreatedAtDesc(Long userId, Long buffetId);
    List<Order> findByBuffetIdOrderByCreatedAtDesc(Long buffetId);


    List<Order> findByBuffetIdAndStatusInOrderByCreatedAtDesc(Long buffetId, List<String> statuses);
}



package com.example.Vkus.repository;

import com.example.Vkus.entity.OrderItemBatch;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemBatchRepository extends JpaRepository<OrderItemBatch, OrderItemBatch.Pk> {
}

package com.example.Vkus.repository;

import com.example.Vkus.entity.BatchDiscount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BatchDiscountRepository extends JpaRepository<BatchDiscount, Long> {
    Optional<BatchDiscount> findByBatchId(Long batchId);
}
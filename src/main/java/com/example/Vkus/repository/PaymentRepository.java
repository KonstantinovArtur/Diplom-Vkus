package com.example.Vkus.repository;

import com.example.Vkus.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findTopByOrderIdOrderByIdDesc(Long orderId);
}

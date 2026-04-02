package com.example.Vkus.repository;

import com.example.Vkus.entity.Cart;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {
    Optional<Cart> findByUserIdAndBuffetId(Long userId, Long buffetId);
}

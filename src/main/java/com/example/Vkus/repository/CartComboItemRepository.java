package com.example.Vkus.repository;

import com.example.Vkus.entity.CartComboItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartComboItemRepository extends JpaRepository<CartComboItem, Long> {
}
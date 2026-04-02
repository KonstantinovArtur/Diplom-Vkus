package com.example.Vkus.repository;

import com.example.Vkus.entity.ComboSlotProduct;
import com.example.Vkus.entity.ComboSlotProductId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ComboSlotProductRepository extends JpaRepository<ComboSlotProduct, ComboSlotProductId> {

    List<ComboSlotProduct> findBySlot_Id(Long slotId);
}
package com.example.Vkus.repository;

import com.example.Vkus.entity.SupplierPrice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupplierPriceRepository extends JpaRepository<SupplierPrice, Long> {

    Optional<SupplierPrice> findBySupplier_IdAndProduct_Id(Long supplierId, Long productId);

    List<SupplierPrice> findBySupplier_IdOrderByProduct_NameAsc(Long supplierId);
}
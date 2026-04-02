package com.example.Vkus.repository;

import com.example.Vkus.entity.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    List<Invoice> findByBuffetIdOrderByCreatedAtDesc(Long buffetId);
    long countBySupplier_Id(Long supplierId);

}

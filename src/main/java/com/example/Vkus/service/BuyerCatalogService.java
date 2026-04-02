package com.example.Vkus.service;

import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BuyerCatalogService {

    private final ProductRepository productRepository;

    public BuyerCatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> findProducts(Long categoryId, String q) {
        String query = (q == null) ? "" : q.trim();
        boolean hasQ = !query.isBlank();
        boolean hasCat = categoryId != null;

        List<Product> list;
        if (hasCat && hasQ) {
            list = productRepository.findAllByCategory_IdAndNameContainingIgnoreCaseOrderByNameAsc(categoryId, query);
        } else if (hasCat) {
            list = productRepository.findAllByCategory_IdOrderByNameAsc(categoryId);
        } else if (hasQ) {
            list = productRepository.findAllByNameContainingIgnoreCaseOrderByNameAsc(query);
        } else {
            list = productRepository.findByIsActiveTrueOrderByNameAsc();
        }

        // страховка: если какой-то репо-метод не фильтрует активность
        return list.stream().filter(p -> Boolean.TRUE.equals(p.getIsActive())).toList();
    }
}

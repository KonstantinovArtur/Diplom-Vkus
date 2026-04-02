package com.example.Vkus.service;

import com.example.Vkus.entity.Product;
import com.example.Vkus.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductCatalogService {

    private final ProductRepository productRepository;

    public ProductCatalogService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public List<Product> listAll() {
        return productRepository.findAllWithCategoryOrdered();
    }
}

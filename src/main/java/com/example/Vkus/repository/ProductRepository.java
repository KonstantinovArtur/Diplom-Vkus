package com.example.Vkus.repository;

import com.example.Vkus.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {



    List<Product> findAllByCategory_IdOrderByNameAsc(Long categoryId);

    List<Product> findAllByNameContainingIgnoreCaseOrderByNameAsc(String q);

    List<Product> findAllByCategory_IdAndNameContainingIgnoreCaseOrderByNameAsc(Long categoryId, String q);
    Optional<Product> findByProductCodeIgnoreCase(String productCode);

    Optional<Product> findByCategory_IdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategory_IdAndNameIgnoreCase(Long categoryId, String name);

    boolean existsByCategory_IdAndNameIgnoreCaseAndIdNot(Long categoryId, String name, Long id);
    @Query("""
    select p
    from Product p
    join fetch p.category c
    left join fetch c.parent cp
    order by coalesce(cp.name, ''), c.name, p.name
""")
    List<Product> findAllWithCategoryOrdered();
    List<Product> findByIsActiveTrueOrderByNameAsc();
}

package com.example.Vkus.repository;

import com.example.Vkus.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    @Query("select ci from CartItem ci " +
            "join fetch ci.product p " +
            "join fetch p.category " +
            "where ci.cart.id = :cartId " +
            "order by ci.createdAt asc")
    List<CartItem> findAllByCartIdWithProduct(Long cartId);

    Optional<CartItem> findByCart_IdAndProduct_Id(Long cartId, Long productId);

    void deleteByCart_IdAndProduct_Id(Long cartId, Long productId);

    void deleteByCart_Id(Long cartId);
}

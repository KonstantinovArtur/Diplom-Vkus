package com.example.Vkus.repository;

import com.example.Vkus.entity.CartCombo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CartComboRepository extends JpaRepository<CartCombo, Long> {

    @Query("""
        select distinct cc from CartCombo cc
        join fetch cc.comboTemplate ct
        left join fetch cc.items it
        left join fetch it.comboSlot s
        left join fetch it.product p
        where cc.cart.id = :cartId
        order by cc.createdAt asc
    """)
    List<CartCombo> findAllByCartIdFull(Long cartId);

    void deleteByCart_Id(Long cartId);
}
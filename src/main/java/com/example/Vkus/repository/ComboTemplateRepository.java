package com.example.Vkus.repository;

import com.example.Vkus.entity.ComboTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ComboTemplateRepository extends JpaRepository<ComboTemplate, Long> {

    @Query("""
        select ct from ComboTemplate ct
        where ct.buffet.id = :buffetId and ct.isActive = true
        order by ct.id desc
    """)
    List<ComboTemplate> findActiveByBuffet(Long buffetId);

    @Query("""
        select distinct ct from ComboTemplate ct
        left join fetch ct.slots s
        left join fetch s.products sp
        left join fetch sp.product p
        where ct.id = :id
    """)
    Optional<ComboTemplate> findByIdFull(Long id);

    List<ComboTemplate> findByBuffet_IdOrderByIdDesc(Long buffetId);

    Optional<ComboTemplate> findByIdAndBuffet_Id(Long id, Long buffetId);

    @Query(value = """
        select exists(
            select 1
            from order_combos oc
            where oc.combo_template_id = :templateId
        )
        """, nativeQuery = true)
    boolean existsInOrders(Long templateId);
}
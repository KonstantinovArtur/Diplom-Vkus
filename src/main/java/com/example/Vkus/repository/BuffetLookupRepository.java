package com.example.Vkus.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface BuffetLookupRepository extends Repository<com.example.Vkus.entity.User, Long> {

    @Query(value = """
        SELECT bf.id, (b.name || ' · ' || bf.name) AS label
        FROM buffets bf
        JOIN buildings b ON b.id = bf.building_id
        WHERE bf.is_active = TRUE
        ORDER BY b.name, bf.name
        """, nativeQuery = true)
    List<Object[]> findActiveBuffetsRaw();
}

package com.example.Vkus.repository;

import com.example.Vkus.entity.Buffet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;
import java.util.List;

public interface BuffetRepository extends JpaRepository<Buffet, Long> {

    boolean existsByBuilding_IdAndNameIgnoreCase(Long buildingId, String name);
    Optional<Buffet> findFirstByIsActiveTrueOrderByIdAsc();
    boolean existsByBuilding_IdAndNameIgnoreCaseAndIdNot(Long buildingId, String name, Long id);

    @Query("""
           select b from Buffet b
           join fetch b.building bd
           order by bd.name asc, b.name asc
           """)
    List<Buffet> findAllWithBuildingOrdered();
}

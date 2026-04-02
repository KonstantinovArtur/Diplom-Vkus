package com.example.Vkus.repository;

import com.example.Vkus.entity.Building;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface BuildingRepository extends JpaRepository<Building, Long> {
    Optional<Building> findByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCase(String name);
    List<Building> findAllByOrderByNameAsc();
}

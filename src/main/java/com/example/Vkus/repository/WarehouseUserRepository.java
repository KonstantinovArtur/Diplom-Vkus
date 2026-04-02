package com.example.Vkus.repository;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import java.util.List;

public interface WarehouseUserRepository extends Repository<com.example.Vkus.entity.User, Long> {

    @Query(value = """
        SELECT u.id, u.full_name, u.email
        FROM users u
        JOIN user_roles ur ON ur.user_id = u.id
        JOIN roles r ON r.id = ur.role_id
        WHERE r.code = 'warehouse'
          AND u.status = 'active'
        ORDER BY u.full_name
        """, nativeQuery = true)
    List<Object[]> findActiveWarehouseUsersRaw();
}

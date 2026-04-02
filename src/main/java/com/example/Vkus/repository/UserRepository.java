package com.example.Vkus.repository;

import com.example.Vkus.entity.User;
import com.example.Vkus.repository.projection.UserRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);
    @Modifying
    @Query(value = "UPDATE users SET default_buffet_id = :buffetId WHERE id = :userId", nativeQuery = true)
    void updateDefaultBuffet(@Param("userId") Long userId, @Param("buffetId") Long buffetId);
    // старое (для OIDC / ролей по email) — оставляем как есть
    @Query(value = """
        SELECT r.code
        FROM roles r
        JOIN user_roles ur ON ur.role_id = r.id
        JOIN users u ON u.id = ur.user_id
        WHERE lower(u.email) = lower(:email)
        """, nativeQuery = true)
    List<String> findRoleCodesByEmail(@Param("email") String email);

    // новое: список пользователей + роли одной строкой (PostgreSQL)
    @Query(value = """
        SELECT
            u.id          AS id,
            u.created_at  AS createdAt,
            u.email       AS email,
            u.full_name   AS fullName,
            u.status      AS status,
            COALESCE(string_agg(r.code, ', ' ORDER BY r.code), '') AS roles
        FROM users u
        LEFT JOIN user_roles ur ON ur.user_id = u.id
        LEFT JOIN roles r ON r.id = ur.role_id
        GROUP BY u.id, u.created_at, u.email, u.full_name, u.status
        ORDER BY u.id
        """, nativeQuery = true)
    List<UserRow> findAllUsersWithRoles();
    Optional<User> findByEmail(String email);
}

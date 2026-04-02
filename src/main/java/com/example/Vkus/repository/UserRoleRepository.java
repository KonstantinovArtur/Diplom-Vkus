package com.example.Vkus.repository;

import com.example.Vkus.entity.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Query("""
        delete from UserRole ur
        where ur.userId = :userId
    """)
    void deleteByUserId(@org.springframework.data.repository.query.Param("userId") Long userId);
}

package com.example.Vkus.repository;

import com.example.Vkus.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RoleRepository extends JpaRepository<Role, Long> {

    List<Role> findAllByOrderByNameAsc();

    boolean existsByCodeIgnoreCase(String code);
    boolean existsByCodeIgnoreCaseAndIdNot(String code, Long id);

    boolean existsByNameIgnoreCase(String name);
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);

    @Query("""
        select ur.roleId
        from UserRole ur
        where ur.userId = :userId
    """)
    List<Long> findRoleIdsByUserId(@Param("userId") Long userId);

    @Query("""
        select r.code
        from Role r
        where r.id in (
            select ur.roleId
            from UserRole ur
            where ur.userId = :userId
        )
        order by r.code
    """)
    List<String> findRoleCodesByUserId(@Param("userId") Long userId);
}
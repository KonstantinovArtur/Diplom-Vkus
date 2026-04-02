package com.example.Vkus.repository;

import com.example.Vkus.entity.AuditLog;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;


import java.util.List;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long>, JpaSpecificationExecutor<AuditLog> {
    @Query("""
        select al
        from AuditLog al
        left join fetch al.actor
        order by al.id asc
    """)
    List<AuditLog> findBatchWithActor(Pageable pageable);
    List<AuditLog> findTop300ByOrderByIdAsc();
}
package com.example.Vkus.repository;

import com.example.Vkus.entity.AuditLogArchiveFile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogArchiveFileRepository extends JpaRepository<AuditLogArchiveFile, Long> {
    List<AuditLogArchiveFile> findAllByOrderByCreatedAtDesc();
}
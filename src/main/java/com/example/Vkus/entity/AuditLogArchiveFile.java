package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log_archive_files")
public class AuditLogArchiveFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "file_name", nullable = false, unique = true)
    private String fileName;

    @Column(name = "log_from_id")
    private Long logFromId;

    @Column(name = "log_to_id")
    private Long logToId;

    @Column(name = "records_count", nullable = false)
    private Integer recordsCount;

    @Lob
    @Column(name = "content_json", columnDefinition = "text")
    private String contentJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getFileName() {
        return fileName;
    }

    public Long getLogFromId() {
        return logFromId;
    }

    public Long getLogToId() {
        return logToId;
    }

    public Integer getRecordsCount() {
        return recordsCount;
    }

    public String getContentJson() {
        return contentJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public void setLogFromId(Long logFromId) {
        this.logFromId = logFromId;
    }

    public void setLogToId(Long logToId) {
        this.logToId = logToId;
    }

    public void setRecordsCount(Integer recordsCount) {
        this.recordsCount = recordsCount;
    }

    public void setContentJson(String contentJson) {
        this.contentJson = contentJson;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
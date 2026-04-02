package com.example.Vkus.service;

import com.example.Vkus.entity.AuditLog;
import com.example.Vkus.entity.AuditLogArchiveFile;
import com.example.Vkus.repository.AuditLogArchiveFileRepository;
import com.example.Vkus.repository.AuditLogRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuditLogArchiveService {

    private static final int BATCH_SIZE = 300;

    private final AuditLogRepository auditLogRepository;
    private final AuditLogArchiveFileRepository archiveFileRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.audit.archive-dir:audit-archive}")
    private String archiveDir;

    public AuditLogArchiveService(AuditLogRepository auditLogRepository,
                                  AuditLogArchiveFileRepository archiveFileRepository,
                                  ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.archiveFileRepository = archiveFileRepository;
        this.objectMapper = objectMapper;

        System.out.println("[AUDIT-ARCHIVE] Service initialized");
    }

    @Scheduled(fixedDelay = 60000)
    @Transactional(readOnly = false)
    public void archiveIfNeeded() {
        try {
            System.out.println("[AUDIT-ARCHIVE] Check started");

            long total = auditLogRepository.count();
            System.out.println("[AUDIT-ARCHIVE] Total logs in DB: " + total);

            if (total < BATCH_SIZE) {
                System.out.println("[AUDIT-ARCHIVE] Not enough logs for archive");
                return;
            }

            List<AuditLog> batch = auditLogRepository.findBatchWithActor(PageRequest.of(0, BATCH_SIZE));
            System.out.println("[AUDIT-ARCHIVE] Batch size: " + batch.size());

            if (batch.isEmpty()) {
                System.out.println("[AUDIT-ARCHIVE] Batch is empty");
                return;
            }

            Path dir = Path.of(archiveDir).toAbsolutePath().normalize();
            Files.createDirectories(dir);
            System.out.println("[AUDIT-ARCHIVE] Archive dir: " + dir);

            Long fromId = batch.get(0).getId();
            Long toId = batch.get(batch.size() - 1).getId();

            String timestamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

            String fileName = "audit_" + timestamp + "_" + fromId + "_" + toId + ".json";
            Path filePath = dir.resolve(fileName);

            List<Map<String, Object>> exportData = batch.stream()
                    .map(this::toExportMap)
                    .toList();

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(filePath.toFile(), exportData);

            System.out.println("[AUDIT-ARCHIVE] File written: " + filePath);

            AuditLogArchiveFile archiveFile = new AuditLogArchiveFile();
            archiveFile.setFileName(fileName);
            archiveFile.setLogFromId(fromId);
            archiveFile.setLogToId(toId);
            archiveFile.setRecordsCount(batch.size());
            archiveFileRepository.save(archiveFile);

            System.out.println("[AUDIT-ARCHIVE] Archive metadata saved");

            auditLogRepository.deleteAllInBatch(batch);

            System.out.println("[AUDIT-ARCHIVE] Archived and deleted " + batch.size() + " logs");
        } catch (Exception e) {
            System.out.println("[AUDIT-ARCHIVE] ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Map<String, Object> toExportMap(AuditLog log) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", log.getId());
        map.put("createdAt", log.getCreatedAt() != null ? log.getCreatedAt().toString() : null);
        map.put("action", log.getAction());
        map.put("entityType", log.getEntityType());
        map.put("entityId", log.getEntityId());

        map.put("actorId", log.getActor() != null ? log.getActor().getId() : null);
        map.put("actorEmail", log.getActor() != null ? log.getActor().getEmail() : null);
        map.put("actorName", log.getActor() != null ? log.getActor().getFullName() : null);

        map.put("metaJson", log.getMetaJson());
        return map;
    }
}
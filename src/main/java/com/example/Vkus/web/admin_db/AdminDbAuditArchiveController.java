package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.AuditLogArchiveFile;
import com.example.Vkus.repository.AuditLogArchiveFileRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin-db/audit-archive")
@PreAuthorize("hasRole('DB_ADMIN')")
public class AdminDbAuditArchiveController {

    private final AuditLogArchiveFileRepository archiveFileRepository;
    private final ObjectMapper objectMapper;

    @Value("${app.audit.archive-dir:audit-archive}")
    private String archiveDir;

    public AdminDbAuditArchiveController(AuditLogArchiveFileRepository archiveFileRepository,
                                         ObjectMapper objectMapper) {
        this.archiveFileRepository = archiveFileRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String archivePage(Model model) {
        List<AuditLogArchiveFile> files = archiveFileRepository.findAllByOrderByCreatedAtDesc();
        model.addAttribute("files", files);
        return "admin-db/audit-archive";
    }

    @GetMapping("/preview/{id}")
    public String preview(@PathVariable Long id,
                          @RequestParam(required = false) String action,
                          @RequestParam(required = false) String entityType,
                          @RequestParam(required = false) String actorName,
                          @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to,
                          Model model) throws Exception {
        AuditLogArchiveFile file = archiveFileRepository.findById(id).orElseThrow();

        Path path = Path.of(archiveDir).resolve(file.getFileName()).normalize();
        if (!Files.exists(path) || !Files.isReadable(path)) {
            throw new RuntimeException("Файл архива не найден");
        }

        List<Map<String, Object>> logs = objectMapper.readValue(
                path.toFile(),
                new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {}
        );

        LocalDateTime fromDt = parseDateTime(from);
        LocalDateTime toDt = parseDateTime(to);

        List<Map<String, Object>> filteredLogs = logs.stream()
                .filter(l -> matchesText(l.get("action"), action))
                .filter(l -> matchesText(l.get("entityType"), entityType))
                .filter(l -> matchesActor(l, actorName))
                .filter(l -> matchesPeriod(l.get("createdAt"), fromDt, toDt))
                .toList();

        model.addAttribute("archiveFile", file);
        model.addAttribute("logs", filteredLogs);
        model.addAttribute("action", action);
        model.addAttribute("entityType", entityType);
        model.addAttribute("actorName", actorName);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "admin-db/audit-archive-preview";
    }

    @GetMapping("/view/{id}")
    public ResponseEntity<Resource> view(@PathVariable Long id) throws Exception {
        AuditLogArchiveFile file = archiveFileRepository.findById(id).orElseThrow();
        Path path = Path.of(archiveDir).resolve(file.getFileName()).normalize();

        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Файл архива не найден");
        }

        String contentType = Files.probeContentType(path);
        if (contentType == null) {
            contentType = MediaType.APPLICATION_JSON_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(file.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws Exception {
        AuditLogArchiveFile file = archiveFileRepository.findById(id).orElseThrow();
        Path path = Path.of(archiveDir).resolve(file.getFileName()).normalize();

        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists() || !resource.isReadable()) {
            throw new RuntimeException("Файл архива не найден");
        }

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(file.getFileName(), StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .body(resource);
    }


    private boolean matchesText(Object value, String filter) {
        if (filter == null || filter.isBlank()) {
            return true;
        }
        String text = value != null ? String.valueOf(value) : "";
        return text.toLowerCase().contains(filter.trim().toLowerCase());
    }

    private boolean matchesActor(Map<String, Object> log, String actorFilter) {
        if (actorFilter == null || actorFilter.isBlank()) {
            return true;
        }

        String actorName = log.get("actorName") != null ? String.valueOf(log.get("actorName")) : "";
        String actorEmail = log.get("actorEmail") != null ? String.valueOf(log.get("actorEmail")) : "";
        String filter = actorFilter.trim().toLowerCase();

        return actorName.toLowerCase().contains(filter) || actorEmail.toLowerCase().contains(filter);
    }

    private boolean matchesPeriod(Object createdAtValue, LocalDateTime from, LocalDateTime to) {
        if (from == null && to == null) {
            return true;
        }

        if (createdAtValue == null) {
            return false;
        }

        try {
            LocalDateTime createdAt = LocalDateTime.parse(String.valueOf(createdAtValue));

            if (from != null && createdAt.isBefore(from)) {
                return false;
            }
            if (to != null && createdAt.isAfter(to)) {
                return false;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception e) {
            return null;
        }
    }

}
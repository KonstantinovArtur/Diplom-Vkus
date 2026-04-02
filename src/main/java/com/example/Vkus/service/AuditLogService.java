package com.example.Vkus.service;

import com.example.Vkus.entity.AuditLog;
import com.example.Vkus.repository.AuditLogRepository;
import com.example.Vkus.security.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.Hibernate;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.*;

@Service
public class AuditLogService {

    private final JdbcTemplate jdbc;
    private final AuditLogRepository repo;
    private final CurrentUserService currentUserService;
    private final ObjectMapper om;

    public AuditLogService(AuditLogRepository repo,
                           CurrentUserService currentUserService,
                           ObjectMapper om,
                           JdbcTemplate jdbc) {
        this.repo = repo;
        this.currentUserService = currentUserService;
        this.om = om;
        this.jdbc = jdbc;
    }

    public void log(String action, String entityType, Long entityId, Map<String, Object> meta) {
        AuditLog l = new AuditLog();

        try {
            l.setActor(currentUserService.getCurrentUser());
        } catch (Exception e) {
            l.setActor(null);
        }

        l.setAction(action);
        l.setEntityType(entityType);
        l.setEntityId(entityId);
        l.setMetaJson((meta != null && !meta.isEmpty()) ? toJsonSafe(meta) : null);

        repo.save(l);
    }

    public void logAs(Long actorUserId, String action, String entityType, Long entityId, Map<String, Object> meta) {
        String json = (meta != null && !meta.isEmpty()) ? toJsonSafe(meta) : null;

        jdbc.update("""
            insert into audit_log(actor_user_id, action, entity_type, entity_id, created_at, meta_json)
            values (?, ?, ?, ?, now(), case when ? is null then null else (?::jsonb) end)
        """, actorUserId, action, entityType, entityId, json, json);
    }

    public void logSimple(String action, Map<String, Object> meta) {
        log(action, null, null, meta);
    }

    private String toJsonSafe(Map<String, Object> meta) {
        try {
            Object normalized = normalize(meta);
            return om.writeValueAsString(normalized);
        } catch (Exception e) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("meta_to_json_error", e.getMessage());
            fallback.put("meta_raw", String.valueOf(meta));
            try {
                return om.writeValueAsString(fallback);
            } catch (Exception ex) {
                return "{\"meta_raw\":\"serialization_failed\"}";
            }
        }
    }

    private Object normalize(Object value) {
        if (value == null) return null;

        if (value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }

        if (value instanceof LocalDate || value instanceof LocalDateTime || value instanceof OffsetDateTime) {
            return value.toString();
        }

        if (value instanceof Enum<?>) {
            return ((Enum<?>) value).name();
        }

        if (value instanceof UUID) {
            return value.toString();
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                normalized.put(String.valueOf(e.getKey()), normalize(e.getValue()));
            }
            return normalized;
        }

        if (value instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>();
            for (Object item : collection) {
                normalized.add(normalize(item));
            }
            return normalized;
        }

        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            List<Object> normalized = new ArrayList<>(len);
            for (int i = 0; i < len; i++) {
                normalized.add(normalize(Array.get(value, i)));
            }
            return normalized;
        }

        if (value instanceof HibernateProxy proxy) {
            Object impl = Hibernate.unproxy(proxy);
            return normalizeEntity(impl);
        }

        if (isEntityLike(value)) {
            return normalizeEntity(value);
        }

        return String.valueOf(value);
    }

    private boolean isEntityLike(Object value) {
        return value.getClass().isAnnotationPresent(jakarta.persistence.Entity.class);
    }

    private Object normalizeEntity(Object entity) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("_entity", entity.getClass().getSimpleName());

        Object id = invokeGetter(entity, "getId");
        if (id == null) id = invokeGetter(entity, "getIdUser");
        if (id == null) id = invokeGetter(entity, "getIdSupplier");
        if (id == null) id = invokeGetter(entity, "getIdProduct");
        if (id == null) id = invokeGetter(entity, "getIdInvoice");
        if (id != null) {
            result.put("id", id);
        }

        Object label = invokeGetter(entity, "getName");
        if (label == null) label = invokeGetter(entity, "getFullName");
        if (label == null) label = invokeGetter(entity, "getEmail");
        if (label == null) label = invokeGetter(entity, "getTitle");
        if (label == null) label = invokeGetter(entity, "getNumber");
        if (label != null) {
            result.put("label", String.valueOf(label));
        }

        return result;
    }

    private Object invokeGetter(Object obj, String methodName) {
        try {
            Method m = obj.getClass().getMethod(methodName);
            return m.invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
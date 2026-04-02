package com.example.Vkus.web.admin_db;

import com.example.Vkus.entity.AuditLog;
import com.example.Vkus.entity.User;
import com.example.Vkus.repository.AuditLogRepository;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/admin-db/audit")
@PreAuthorize("hasRole('DB_ADMIN')")
public class AdminDbAuditController {

    private final AuditLogRepository repo;

    public AdminDbAuditController(AuditLogRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public String page(
            Model model,
            @RequestParam(value = "action", required = false) String action,
            @RequestParam(value = "entityType", required = false) String entityType,
            @RequestParam(value = "actorName", required = false) String actorName,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "to", required = false) String to
    ) {
        LocalDateTime fromDt = parseDt(from);
        LocalDateTime toDt = parseDt(to);

        Specification<AuditLog> spec = (root, q, cb) -> {

            // IMPORTANT: fetch join чтобы не было LazyInitializationException
            root.fetch("actor", JoinType.LEFT);

            List<Predicate> ps = new ArrayList<>();

            if (action != null && !action.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("action")),
                        "%" + action.toLowerCase() + "%"));
            }

            if (entityType != null && !entityType.isBlank()) {
                ps.add(cb.like(cb.lower(root.get("entityType")),
                        "%" + entityType.toLowerCase() + "%"));
            }

            if (actorName != null && !actorName.isBlank()) {
                Join<AuditLog, User> actorJoin =
                        root.join("actor", JoinType.LEFT);

                ps.add(cb.like(cb.lower(actorJoin.get("fullName")),
                        "%" + actorName.toLowerCase() + "%"));
            }

            if (fromDt != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("createdAt"), fromDt));
            }

            if (toDt != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("createdAt"), toDt));
            }

            return cb.and(ps.toArray(new Predicate[0]));
        };

        List<AuditLog> logs =
                repo.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));

        model.addAttribute("logs", logs);
        model.addAttribute("action", action);
        model.addAttribute("entityType", entityType);
        model.addAttribute("actorName", actorName);
        model.addAttribute("from", from);
        model.addAttribute("to", to);

        return "admin-db/audit";
    }

    private LocalDateTime parseDt(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDateTime.parse(s);
        } catch (Exception e) {
            return null;
        }
    }
}
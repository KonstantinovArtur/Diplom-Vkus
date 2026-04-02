package com.example.Vkus.web.admin_buffet;

import com.example.Vkus.entity.BatchDiscount;
import com.example.Vkus.repository.BatchDiscountRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.web.dto.BatchDiscountForm;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/admin-buffet/batches")
public class BuffetAdminBatchesController {

    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbc;
    private final BatchDiscountRepository batchDiscountRepository;
    private final AuditLogService audit;

    public record BatchRow(
            Long batchId,
            Long productId,
            String productName,
            String categoryName,
            LocalDateTime receivedAt,
            LocalDate expiresAt,
            Integer qtyReceived,
            Integer qtyAvailable,
            String status,
            Long discountId,
            BigDecimal discountPercent,
            Boolean discountActive
    ) {}

    public BuffetAdminBatchesController(CurrentUserService currentUserService,
                                        JdbcTemplate jdbc,
                                        BatchDiscountRepository batchDiscountRepository,
                                        AuditLogService audit) {
        this.currentUserService = currentUserService;
        this.jdbc = jdbc;
        this.batchDiscountRepository = batchDiscountRepository;
        this.audit = audit;
    }

    @GetMapping
    public String page(@RequestParam(name = "productId", required = false) Long productId,
                       @RequestParam(name = "onlyActive", required = false, defaultValue = "true") boolean onlyActive,
                       Model model,
                       @ModelAttribute("form") BatchDiscountForm form) {

        long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        StringBuilder sql = new StringBuilder("""
        SELECT b.id AS batch_id,
               p.id AS product_id,
               p.name AS product_name,
               c.name AS category_name,
               b.received_at,
               b.expires_at,
               b.qty_received,
               b.qty_available,
               b.status,
               bd.id AS discount_id,
               bd.percent AS discount_percent,
               bd.is_active AS discount_active
        FROM inventory_batches b
        JOIN products p ON p.id = b.product_id
        JOIN categories c ON c.id = p.category_id
        LEFT JOIN batch_discounts bd ON bd.batch_id = b.id
        WHERE b.buffet_id = ?
    """);

        List<Object> args = new ArrayList<>();
        args.add(buffetId);

        if (productId != null) {
            sql.append(" AND b.product_id = ? ");
            args.add(productId);
        }

        if (onlyActive) {
            sql.append(" AND (b.status = 'active' AND b.qty_available > 0) ");
        }

        sql.append("""
        ORDER BY
          (b.expires_at IS NULL), b.expires_at, b.received_at DESC, b.id DESC
    """);

        List<BatchRow> rows = jdbc.query(
                sql.toString(),
                args.toArray(),
                (rs, rn) -> new BatchRow(
                        rs.getLong("batch_id"),
                        rs.getLong("product_id"),
                        rs.getString("product_name"),
                        rs.getString("category_name"),
                        rs.getTimestamp("received_at").toLocalDateTime(),
                        rs.getObject("expires_at", LocalDate.class),
                        rs.getInt("qty_received"),
                        rs.getInt("qty_available"),
                        rs.getString("status"),
                        (Long) rs.getObject("discount_id"),
                        rs.getBigDecimal("discount_percent"),
                        (Boolean) rs.getObject("discount_active")
                )
        );

        model.addAttribute("rows", rows);
        model.addAttribute("productId", productId);
        model.addAttribute("onlyActive", onlyActive);
        model.addAttribute("today", LocalDate.now());

        return "admin-buffet/batches";
    }

    @PostMapping("/discount/save")
    @Transactional
    public String saveDiscount(@Valid @ModelAttribute("form") BatchDiscountForm form,
                               BindingResult br,
                               RedirectAttributes ra) {

        long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        long actorId = currentUserService.getCurrentUser().getId();

        // проверка: партия должна принадлежать буфету
        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_batches WHERE id=? AND buffet_id=?",
                Integer.class, form.getBatchId(), buffetId
        );
        if (cnt == null || cnt == 0) {
            ra.addFlashAttribute("err", "Партия не найдена или не принадлежит вашему буфету");
            return "redirect:/admin-buffet/batches";
        }

        if (br.hasErrors()) {
            ra.addFlashAttribute("err", "Проверьте поля скидки (процент/активность)");
            return "redirect:/admin-buffet/batches";
        }

        BatchDiscount before = batchDiscountRepository.findByBatchId(form.getBatchId()).orElse(null);

        BatchDiscount bd = before != null ? before : new BatchDiscount();
        bd.setBatchId(form.getBatchId());
        bd.setPercent(form.getPercent());
        bd.setIsActive(form.getIsActive());
        if (before == null) bd.setCreatedBy(actorId);

        batchDiscountRepository.save(bd);

        audit.log(before == null ? "BATCH_DISCOUNT_CREATE" : "BATCH_DISCOUNT_UPDATE",
                "inventory_batch",
                form.getBatchId(),
                Map.of(
                        "actorUserId", actorId,
                        "buffetId", buffetId,
                        "before", snapshotBatchDiscount(before),
                        "after", snapshotBatchDiscount(bd)
                )
        );

        ra.addFlashAttribute("ok", "Скидка по партии сохранена");
        return "redirect:/admin-buffet/batches";
    }

    @PostMapping("/discount/deactivate")
    @Transactional
    public String deactivate(@RequestParam Long batchId, RedirectAttributes ra) {

        long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        long actorId = currentUserService.getCurrentUser().getId();

        Integer cnt = jdbc.queryForObject(
                "SELECT COUNT(*) FROM inventory_batches WHERE id=? AND buffet_id=?",
                Integer.class, batchId, buffetId
        );
        if (cnt == null || cnt == 0) {
            ra.addFlashAttribute("err", "Партия не найдена или не принадлежит вашему буфету");
            return "redirect:/admin-buffet/batches";
        }

        BatchDiscount bd = batchDiscountRepository.findByBatchId(batchId).orElse(null);
        if (bd == null) {
            ra.addFlashAttribute("ok", "Скидки на эту партию нет");
            return "redirect:/admin-buffet/batches";
        }

        BatchDiscount before = bd;
        bd.setIsActive(false);
        batchDiscountRepository.save(bd);

        audit.log("BATCH_DISCOUNT_DEACTIVATE", "inventory_batch", batchId, Map.of(
                "actorUserId", actorId,
                "buffetId", buffetId,
                "before", snapshotBatchDiscount(before),
                "after", snapshotBatchDiscount(bd)
        ));

        ra.addFlashAttribute("ok", "Скидка выключена");
        return "redirect:/admin-buffet/batches";
    }

    private Map<String, Object> snapshotBatchDiscount(BatchDiscount bd) {
        if (bd == null) return Map.of("exists", false);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("exists", true);
        m.put("id", bd.getId());
        m.put("batchId", bd.getBatchId());
        m.put("percent", bd.getPercent());
        m.put("isActive", bd.getIsActive());
        m.put("createdBy", bd.getCreatedBy());
        m.put("createdAt", bd.getCreatedAt());
        return m;
    }
}
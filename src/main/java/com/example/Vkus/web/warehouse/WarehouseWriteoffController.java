package com.example.Vkus.web.warehouse;

import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.security.CurrentUserService;
import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.WriteoffService;
import com.example.Vkus.web.dto.BatchOption;
import com.example.Vkus.web.dto.WriteoffCreateForm;
import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/warehouse/writeoffs")
public class WarehouseWriteoffController {

    private final CurrentUserService currentUserService;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbc;
    private final WriteoffService writeoffService;
    private final AuditLogService audit;

    public WarehouseWriteoffController(CurrentUserService currentUserService,
                                       ProductRepository productRepository,
                                       JdbcTemplate jdbc,
                                       WriteoffService writeoffService,
                                       AuditLogService audit) {
        this.currentUserService = currentUserService;
        this.productRepository = productRepository;
        this.jdbc = jdbc;
        this.writeoffService = writeoffService;
        this.audit = audit;
    }

    @GetMapping("/new")
    public String form(Model model, @ModelAttribute("form") WriteoffCreateForm form) {
        model.addAttribute("products", productRepository.findByIsActiveTrueOrderByNameAsc());
        return "warehouse/writeoffs/new";
    }

    @GetMapping("/batches")
    @ResponseBody
    public List<BatchOption> batches(@RequestParam Long productId) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        return jdbc.query(
                """
                SELECT id, received_at, expires_at, qty_available
                FROM inventory_batches
                WHERE buffet_id = ?
                  AND product_id = ?
                  AND status = 'active'
                  AND qty_available > 0
                ORDER BY
                  CASE WHEN expires_at IS NULL THEN 1 ELSE 0 END,
                  expires_at ASC,
                  received_at ASC
                """,
                (rs, rn) -> {
                    long id = rs.getLong("id");
                    LocalDateTime receivedAt = rs.getTimestamp("received_at").toLocalDateTime();
                    LocalDate expiresAt = rs.getObject("expires_at", LocalDate.class);
                    int qtyAvail = rs.getInt("qty_available");

                    String exp = (expiresAt == null) ? "без срока" : ("годен до " + expiresAt);
                    String label = "#" + id + " · " + exp + " · доступно " + qtyAvail + " · поступила " + receivedAt.toLocalDate();

                    return new BatchOption(id, qtyAvail, expiresAt, receivedAt, label);
                },
                buffetId, productId
        );
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") WriteoffCreateForm form,
                         BindingResult br,
                         Model model,
                         RedirectAttributes ra) {

        if (br.hasErrors()) {
            model.addAttribute("products", productRepository.findByIsActiveTrueOrderByNameAsc());
            return "warehouse/writeoffs/new";
        }

        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();
        Long actorId = currentUserService.getCurrentUser().getId();

        try {
            long docId = writeoffService.createWriteoff(buffetId, actorId, form);

            audit.log("WRITEOFF_CREATE", "writeoff", docId, Map.of(
                    "after", snapshotWriteoff(docId, buffetId, actorId, form)
            ));

            ra.addFlashAttribute("ok", "Списание проведено. Документ #" + docId);
            return "redirect:/warehouse/writeoffs/new";

        } catch (IllegalStateException e) {
            model.addAttribute("products", productRepository.findByIsActiveTrueOrderByNameAsc());
            model.addAttribute("err", e.getMessage());
            return "warehouse/writeoffs/new";
        }
    }

    private Map<String, Object> snapshotWriteoff(long docId, long buffetId, long actorId, WriteoffCreateForm f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("docId", docId);
        m.put("buffetId", buffetId);
        m.put("actorUserId", actorId);
        m.put("productId", f.getProductId());
        m.put("batchId", f.getBatchId());
        m.put("qty", f.getQty());
        m.put("reason", f.getReason());
        m.put("comment", f.getComment());
        return m;
    }
}
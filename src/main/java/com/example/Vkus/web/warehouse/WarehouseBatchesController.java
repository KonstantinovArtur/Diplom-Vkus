package com.example.Vkus.web.warehouse;

import com.example.Vkus.security.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/warehouse/batches")
public class WarehouseBatchesController {

    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbcTemplate;

    public WarehouseBatchesController(CurrentUserService currentUserService, JdbcTemplate jdbcTemplate) {
        this.currentUserService = currentUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public String list(Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        String sql = """
            SELECT b.id,
                   p.name AS product_name,
                   b.received_at,
                   b.expires_at,
                   b.qty_received,
                   b.qty_available,
                   b.status
            FROM inventory_batches b
            JOIN products p ON p.id = b.product_id
            WHERE b.buffet_id = ?
            ORDER BY b.status ASC, b.expires_at NULLS LAST, b.received_at DESC, b.id DESC
        """;

        List<?> rows = jdbcTemplate.queryForList(sql, buffetId);
        model.addAttribute("rows", rows);
        return "warehouse/batches/list";
    }
}

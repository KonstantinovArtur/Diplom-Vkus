package com.example.Vkus.web.warehouse;

import com.example.Vkus.security.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequestMapping("/warehouse/movements")
public class WarehouseMovementsController {

    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbcTemplate;

    public WarehouseMovementsController(CurrentUserService currentUserService, JdbcTemplate jdbcTemplate) {
        this.currentUserService = currentUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public String list(Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        String sql = """
            SELECT m.id,
                   p.name AS product_name,
                   m.type,
                   m.qty,
                   m.batch_id,
                   m.ref_type,
                   m.ref_id,
                   m.created_at,
                   u.full_name AS created_by_name
            FROM inventory_movements m
            JOIN products p ON p.id = m.product_id
            JOIN users u ON u.id = m.created_by
            WHERE m.buffet_id = ?
            ORDER BY m.created_at DESC, m.id DESC
            LIMIT 300
        """;

        List<?> rows = jdbcTemplate.queryForList(sql, buffetId);
        model.addAttribute("rows", rows);
        return "warehouse/movements/list";
    }
}

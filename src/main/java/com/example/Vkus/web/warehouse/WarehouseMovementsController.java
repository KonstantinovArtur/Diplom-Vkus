package com.example.Vkus.web.warehouse;

import com.example.Vkus.security.CurrentUserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
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
    public String list(@RequestParam(name = "supplierId", required = false) Long supplierId,
                       @RequestParam(name = "productId", required = false) Long productId,
                       @RequestParam(name = "type", required = false) String type,
                       Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        String normalizedType = (type != null && !type.isBlank()) ? type.trim() : null;

        StringBuilder sql = new StringBuilder("""
        SELECT m.id,
               p.id AS product_id,
               p.name AS product_name,
               sup.supplier_id,
               sup.supplier_name,
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
        LEFT JOIN LATERAL (
            SELECT s.id AS supplier_id,
                   s.name AS supplier_name
            FROM inventory_movements m2
            JOIN invoices i
              ON i.id = m2.ref_id
             AND m2.ref_type = 'invoice'
            JOIN suppliers s ON s.id = i.supplier_id
            WHERE m2.batch_id = m.batch_id
              AND m2.type = 'receipt'
            ORDER BY m2.id
            LIMIT 1
        ) sup ON TRUE
        WHERE m.buffet_id = ?
    """);

        List<Object> params = new ArrayList<>();
        params.add(buffetId);

        if (supplierId != null) {
            sql.append(" AND sup.supplier_id = ?");
            params.add(supplierId);
        }

        if (productId != null) {
            sql.append(" AND m.product_id = ?");
            params.add(productId);
        }

        if (normalizedType != null) {
            sql.append(" AND m.type = ?");
            params.add(normalizedType);
        }

        sql.append("""
        ORDER BY m.created_at DESC, m.id DESC
        LIMIT 300
    """);

        List<?> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<?> suppliers = jdbcTemplate.queryForList("""
        SELECT DISTINCT s.id, s.name
        FROM inventory_movements m
        JOIN inventory_movements r
          ON r.batch_id = m.batch_id
         AND r.type = 'receipt'
        JOIN invoices i
          ON i.id = r.ref_id
         AND r.ref_type = 'invoice'
        JOIN suppliers s ON s.id = i.supplier_id
        WHERE m.buffet_id = ?
        ORDER BY s.name
    """, buffetId);

        List<?> products = jdbcTemplate.queryForList("""
        SELECT DISTINCT p.id, p.name
        FROM inventory_movements m
        JOIN products p ON p.id = m.product_id
        WHERE m.buffet_id = ?
        ORDER BY p.name
    """, buffetId);

        model.addAttribute("rows", rows);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("products", products);
        model.addAttribute("selectedSupplierId", supplierId);
        model.addAttribute("selectedProductId", productId);
        model.addAttribute("selectedType", normalizedType);

        return "warehouse/movements/list";
    }
}

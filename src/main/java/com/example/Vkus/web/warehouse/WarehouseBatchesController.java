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
@RequestMapping("/warehouse/batches")
public class WarehouseBatchesController {

    private final CurrentUserService currentUserService;
    private final JdbcTemplate jdbcTemplate;

    public WarehouseBatchesController(CurrentUserService currentUserService, JdbcTemplate jdbcTemplate) {
        this.currentUserService = currentUserService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping
    public String list(@RequestParam(name = "supplierId", required = false) Long supplierId,
                       @RequestParam(name = "productId", required = false) Long productId,
                       @RequestParam(name = "status", required = false) String status,
                       Model model) {
        Long buffetId = currentUserService.getCurrentBuffetIdOrThrow();

        String normalizedStatus = (status != null && !status.isBlank()) ? status.trim() : null;

        StringBuilder sql = new StringBuilder("""
        SELECT b.id,
               p.id AS product_id,
               p.name AS product_name,
               sup.supplier_id,
               sup.supplier_name,
               b.received_at,
               b.expires_at,
               b.qty_received,
               b.qty_available,
               b.status
        FROM inventory_batches b
        JOIN products p ON p.id = b.product_id
        LEFT JOIN LATERAL (
            SELECT s.id AS supplier_id,
                   s.name AS supplier_name
            FROM inventory_movements m
            JOIN invoices i
              ON i.id = m.ref_id
             AND m.ref_type = 'invoice'
            JOIN suppliers s ON s.id = i.supplier_id
            WHERE m.batch_id = b.id
              AND m.type = 'receipt'
            ORDER BY m.id
            LIMIT 1
        ) sup ON TRUE
        WHERE b.buffet_id = ?
    """);

        List<Object> params = new ArrayList<>();
        params.add(buffetId);

        if (supplierId != null) {
            sql.append(" AND sup.supplier_id = ?");
            params.add(supplierId);
        }

        if (productId != null) {
            sql.append(" AND b.product_id = ?");
            params.add(productId);
        }

        if (normalizedStatus != null) {
            sql.append(" AND b.status = ?");
            params.add(normalizedStatus);
        }

        sql.append("""
        ORDER BY b.status ASC, b.expires_at NULLS LAST, b.received_at DESC, b.id DESC
    """);

        List<?> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());

        List<?> suppliers = jdbcTemplate.queryForList("""
        SELECT DISTINCT s.id, s.name
        FROM invoices i
        JOIN suppliers s ON s.id = i.supplier_id
        WHERE i.buffet_id = ?
        ORDER BY s.name
    """, buffetId);

        List<?> products = jdbcTemplate.queryForList("""
        SELECT DISTINCT p.id, p.name
        FROM inventory_batches b
        JOIN products p ON p.id = b.product_id
        WHERE b.buffet_id = ?
        ORDER BY p.name
    """, buffetId);

        model.addAttribute("rows", rows);
        model.addAttribute("suppliers", suppliers);
        model.addAttribute("products", products);
        model.addAttribute("selectedSupplierId", supplierId);
        model.addAttribute("selectedProductId", productId);
        model.addAttribute("selectedStatus", normalizedStatus);

        return "warehouse/batches/list";
    }
}

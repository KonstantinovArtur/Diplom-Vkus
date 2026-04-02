package com.example.Vkus.service;

import com.example.Vkus.web.dto.SellerInvoiceCheckForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class SellerInvoicesService {

    private static final Set<String> LESS_REASONS = Set.of("defect", "shortage", "missing");
    private static final String GREATER_REASON = "quantity_error";
    private static final String OK_REASON = "ok";

    private final JdbcTemplate jdbc;

    public SellerInvoicesService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record InvoiceRow(
            Long id,
            String supplierName,
            String invoiceNumber,
            LocalDate invoiceDate,
            String status
    ) {}

    public record InvoiceHeader(
            Long id,
            Long buffetId,
            String buffetLabel,
            Long supplierId,
            String supplierName,
            String invoiceNumber,
            LocalDate invoiceDate,
            String status
    ) {}

    public record InvoiceItemRow(
            Long id,
            Long productId,
            String productName,
            Integer qty,
            Integer receivedQty,
            String acceptanceReason
    ) {}

    public List<InvoiceRow> listForBuffet(Long buffetId) {
        String sql = """
            SELECT i.id,
                   s.name AS supplier_name,
                   i.invoice_number,
                   i.invoice_date,
                   i.status
            FROM invoices i
            JOIN suppliers s ON s.id = i.supplier_id
            WHERE i.buffet_id = ?
              AND i.status IN ('ready_for_check', 'checked', 'posted')
            ORDER BY i.id DESC
        """;

        return jdbc.query(sql, (rs, n) -> new InvoiceRow(
                rs.getLong("id"),
                rs.getString("supplier_name"),
                rs.getString("invoice_number"),
                rs.getDate("invoice_date").toLocalDate(),
                rs.getString("status")
        ), buffetId);
    }

    public InvoiceHeader getHeaderForSeller(Long invoiceId, Long buffetId) {
        String sql = """
            SELECT i.id, i.buffet_id,
                   b.name AS buffet_label,
                   i.supplier_id, s.name AS supplier_name,
                   i.invoice_number, i.invoice_date, i.status
            FROM invoices i
            JOIN suppliers s ON s.id = i.supplier_id
            JOIN buffets b ON b.id = i.buffet_id
            WHERE i.id = ?
              AND i.buffet_id = ?
              AND i.status IN ('ready_for_check', 'checked', 'posted')
        """;

        return jdbc.query(sql, rs -> {
            if (!rs.next()) {
                throw new IllegalStateException("Накладная не найдена или недоступна для вашего буфета");
            }
            return new InvoiceHeader(
                    rs.getLong("id"),
                    rs.getLong("buffet_id"),
                    rs.getString("buffet_label"),
                    rs.getLong("supplier_id"),
                    rs.getString("supplier_name"),
                    rs.getString("invoice_number"),
                    rs.getDate("invoice_date").toLocalDate(),
                    rs.getString("status")
            );
        }, invoiceId, buffetId);
    }

    public List<InvoiceItemRow> getItems(Long invoiceId) {
        String sql = """
            SELECT ii.id,
                   ii.product_id,
                   p.name AS product_name,
                   ii.qty,
                   ii.qty_received,
                   ii.acceptance_reason
            FROM invoice_items ii
            JOIN products p ON p.id = ii.product_id
            WHERE ii.invoice_id = ?
            ORDER BY ii.id
        """;

        return jdbc.query(sql, (rs, n) -> new InvoiceItemRow(
                rs.getLong("id"),
                rs.getLong("product_id"),
                rs.getString("product_name"),
                rs.getInt("qty"),
                (Integer) rs.getObject("qty_received"),
                rs.getString("acceptance_reason")
        ), invoiceId);
    }

    @Transactional
    public void checkInvoice(Long invoiceId,
                             Long buffetId,
                             Long sellerUserId,
                             List<SellerInvoiceCheckForm.Item> items) {

        String status = jdbc.query(
                "SELECT status FROM invoices WHERE id = ? AND buffet_id = ?",
                rs -> rs.next() ? rs.getString("status") : null,
                invoiceId, buffetId
        );

        if (status == null) {
            throw new IllegalStateException("Накладная не найдена");
        }

        if (!"ready_for_check".equals(status)) {
            throw new IllegalStateException("Проверка возможна только для накладной в статусе ready_for_check");
        }

        String qtySql = """
            SELECT qty
            FROM invoice_items
            WHERE id = ? AND invoice_id = ?
        """;

        String upd = """
            UPDATE invoice_items
            SET qty_received = ?,
                acceptance_reason = ?
            WHERE id = ? AND invoice_id = ?
        """;

        for (var it : items) {
            Integer received = it.getReceivedQty();
            if (received == null || received < 0) {
                throw new IllegalStateException("Фактическое количество не может быть отрицательным");
            }

            Integer expected = jdbc.query(
                    qtySql,
                    rs -> rs.next() ? rs.getInt("qty") : null,
                    it.getInvoiceItemId(), invoiceId
            );

            if (expected == null) {
                throw new IllegalStateException("Позиция накладной не найдена: " + it.getInvoiceItemId());
            }

            String reason = normalize(it.getAcceptanceReason());

            if (received.equals(expected)) {
                reason = OK_REASON;
            } else if (received < expected) {
                if (reason == null) {
                    throw new IllegalStateException(
                            "Для позиции #" + it.getInvoiceItemId() +
                                    " нужно указать причину, если фактическое количество меньше ожидаемого"
                    );
                }
                if (!LESS_REASONS.contains(reason)) {
                    throw new IllegalStateException(
                            "Для позиции #" + it.getInvoiceItemId() +
                                    " при недоприёмке допустимы только причины: defect, shortage, missing"
                    );
                }
            } else {
                if (reason == null) {
                    throw new IllegalStateException(
                            "Для позиции #" + it.getInvoiceItemId() +
                                    " нужно указать причину, если фактическое количество больше ожидаемого"
                    );
                }
                if (!GREATER_REASON.equals(reason)) {
                    throw new IllegalStateException(
                            "Для позиции #" + it.getInvoiceItemId() +
                                    " при излишке допустима только причина: quantity_error"
                    );
                }
            }

            jdbc.update(upd, received, reason, it.getInvoiceItemId(), invoiceId);
        }

        jdbc.update("""
            UPDATE invoices
            SET status = 'checked',
                checked_at = now(),
                checked_by = ?
            WHERE id = ? AND buffet_id = ?
        """, sellerUserId, invoiceId, buffetId);
    }

    private String normalize(String value) {
        if (value == null) return null;
        String s = value.trim();
        return s.isEmpty() ? null : s;
    }
}
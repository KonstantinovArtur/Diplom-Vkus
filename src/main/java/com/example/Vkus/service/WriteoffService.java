package com.example.Vkus.service;

import com.example.Vkus.web.dto.WriteoffCreateForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;

@Service
public class WriteoffService {

    private final JdbcTemplate jdbc;

    public WriteoffService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public long createWriteoff(Long buffetId, Long actorUserId, WriteoffCreateForm form) {

        // 1) создаём документ списания
        long docId = insertWriteoffDoc(buffetId, actorUserId, form.getReason(), form.getComment());

        // 2) блокируем выбранную партию (FOR UPDATE), проверяем что она этого буфета и товара
        BatchRow batch = jdbc.queryForObject(
                """
                SELECT id, buffet_id, product_id, qty_available, status
                FROM inventory_batches
                WHERE id = ?
                FOR UPDATE
                """,
                (rs, rn) -> new BatchRow(
                        rs.getLong("id"),
                        rs.getLong("buffet_id"),
                        rs.getLong("product_id"),
                        rs.getInt("qty_available"),
                        rs.getString("status")
                ),
                form.getBatchId()
        );

        if (batch == null) throw new IllegalStateException("Партия не найдена");

        if (!buffetId.equals(batch.buffetId())) {
            throw new IllegalStateException("Нет доступа к партии другого буфета");
        }
        if (!form.getProductId().equals(batch.productId())) {
            throw new IllegalStateException("Выбранная партия не соответствует выбранному товару");
        }
        if (!"active".equals(batch.status())) {
            throw new IllegalStateException("Нельзя списать из закрытой партии");
        }

        int qty = form.getQty();
        if (qty > batch.qtyAvailable()) {
            throw new IllegalStateException("Недостаточно доступного количества в партии");
        }

        // 3) уменьшаем qty_available у партии + если стало 0 — закрываем
        jdbc.update(
                """
                UPDATE inventory_batches
                SET qty_available = qty_available - ?,
                    status = CASE WHEN qty_available - ? = 0 THEN 'closed' ELSE status END
                WHERE id = ?
                """,
                qty, qty, batch.id()
        );

        // 4) уменьшаем общий остаток inventory_items (не даём уйти в минус)
        int updated = jdbc.update(
                """
                UPDATE inventory_items
                SET quantity = quantity - ?,
                    updated_at = now()
                WHERE buffet_id = ? AND product_id = ? AND quantity >= ?
                """,
                qty, buffetId, batch.productId(), qty
        );

        if (updated == 0) {
            // если вдруг inventory_items не создан (теоретически), или меньше чем qty
            throw new IllegalStateException("Нельзя списать: общий остаток меньше списания или записи inventory_items нет");
        }

        // 5) пишем позицию документа
        jdbc.update(
                """
                INSERT INTO writeoff_items (writeoff_doc_id, product_id, batch_id, qty)
                VALUES (?, ?, ?, ?)
                """,
                docId, batch.productId(), batch.id(), qty
        );

        // после UPDATE inventory_batches ...
        jdbc.update(
                """
                UPDATE batch_discounts
                SET is_active = FALSE
                WHERE batch_id = ?
                  AND is_active = TRUE
                  AND EXISTS (
                      SELECT 1 FROM inventory_batches b
                      WHERE b.id = ? AND b.status = 'closed'
                  )
                """,
                batch.id(), batch.id()
        );

        // 6) пишем движение склада (qty отрицательное)
        jdbc.update(
                """
                INSERT INTO inventory_movements
                  (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
                VALUES
                  (?, ?, ?, 'writeoff', ?, now(), ?, 'writeoff', ?)
                """,
                buffetId, batch.productId(), batch.id(),
                -qty,
                actorUserId,
                docId
        );

        return docId;
    }

    private long insertWriteoffDoc(Long buffetId, Long createdBy, String reason, String comment) {
        KeyHolder kh = new GeneratedKeyHolder();

        jdbc.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO writeoff_docs (buffet_id, reason, comment, created_at, created_by)
                    VALUES (?, ?, ?, now(), ?)
                    """,
                    new String[]{"id"}
            );
            ps.setLong(1, buffetId);
            ps.setString(2, reason);
            ps.setString(3, comment);
            ps.setLong(4, createdBy);
            return ps;
        }, kh);

        Number key = kh.getKey();
        if (key == null) throw new IllegalStateException("Не удалось создать документ списания");
        return key.longValue();
    }

    private record BatchRow(long id, long buffetId, long productId, int qtyAvailable, String status) {}
}

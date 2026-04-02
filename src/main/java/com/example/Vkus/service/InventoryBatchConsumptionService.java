package com.example.Vkus.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class InventoryBatchConsumptionService {

    private final JdbcTemplate jdbc;

    public InventoryBatchConsumptionService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record BatchTake(long batchId, int qtyTaken) {}

    @Transactional
    public void insertMovement(long buffetId,
                               long productId,
                               long batchId,
                               String type,
                               int qty,
                               long createdBy,
                               String refType,
                               Long refId) {

        jdbc.update("""
            INSERT INTO inventory_movements
              (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
            VALUES
              (?, ?, ?, ?, ?, now(), ?, ?, ?)
            """,
                buffetId,
                productId,
                batchId,
                type,
                qty,
                createdBy,
                refType,
                refId
        );
    }

    /**
     * План списания по FEFO БЕЗ UPDATE (используем для точного расчёта цен в корзине).
     */
    @Transactional(readOnly = true)
    public List<BatchTake> planConsumeFromBatches(long buffetId, long productId, int qty) {
        if (qty <= 0) return List.of();

        Integer totalAvail = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(qty_available),0)
                FROM inventory_batches
                WHERE buffet_id=? AND product_id=? AND status='active' AND qty_available>0
                """,
                Integer.class, buffetId, productId
        );

        int total = (totalAvail == null) ? 0 : totalAvail;
        if (total < qty) {
            throw new IllegalStateException("Недостаточно товара в партиях. Нужно: " + qty + ", доступно: " + total);
        }

        List<long[]> rows = jdbc.query(
                """
                SELECT id, qty_available
                FROM inventory_batches
                WHERE buffet_id=? AND product_id=? AND status='active' AND qty_available>0
                ORDER BY (expires_at IS NULL), expires_at, received_at, id
                """,
                (rs, n) -> new long[]{ rs.getLong("id"), rs.getLong("qty_available") },
                buffetId, productId
        );

        int remaining = qty;
        List<BatchTake> takes = new ArrayList<>();

        for (long[] r : rows) {
            if (remaining == 0) break;

            long batchId = r[0];
            int avail = (int) r[1];
            int take = Math.min(avail, remaining);

            takes.add(new BatchTake(batchId, take));
            remaining -= take;
        }

        if (remaining != 0) {
            throw new IllegalStateException("planConsumeFromBatches: internal error, remaining=" + remaining);
        }

        return takes;
    }

    /**
     * Активные batch-скидки по списку batch_id.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> loadActiveBatchDiscountPercents(List<Long> batchIds) {
        if (batchIds == null || batchIds.isEmpty()) return Map.of();

        // IN (?, ?, ?)
        String inSql = String.join(",", batchIds.stream().map(x -> "?").toList());
        String sql = """
            SELECT batch_id, percent
            FROM batch_discounts
            WHERE is_active = TRUE
              AND batch_id IN (%s)
            """.formatted(inSql);

        Map<Long, BigDecimal> map = new HashMap<>();
        Object[] args = batchIds.toArray();
        jdbc.query(sql, args, rs -> {
            map.put(rs.getLong("batch_id"), rs.getBigDecimal("percent"));
        });        return map;
    }

    /**
     * Реальное списание (как было): делает UPDATE, закрывает партию, и снимает batch-discount, если партия закрылась.
     */
    @Transactional
    public List<BatchTake> consumeFromBatches(long buffetId, long productId, int qty) {
        if (qty <= 0) throw new IllegalArgumentException("qty must be > 0");

        Integer totalAvail = jdbc.queryForObject(
                """
                SELECT COALESCE(SUM(qty_available),0)
                FROM inventory_batches
                WHERE buffet_id=? AND product_id=? AND status='active' AND qty_available>0
                """,
                Integer.class, buffetId, productId
        );

        int total = (totalAvail == null) ? 0 : totalAvail;
        if (total < qty) {
            throw new IllegalStateException("Недостаточно товара в партиях. Нужно: " + qty + ", доступно: " + total);
        }

        List<long[]> rows = jdbc.query(
                """
                SELECT id, qty_available
                FROM inventory_batches
                WHERE buffet_id=? AND product_id=? AND status='active' AND qty_available>0
                ORDER BY (expires_at IS NULL), expires_at, received_at, id
                FOR UPDATE
                """,
                (rs, n) -> new long[]{ rs.getLong("id"), rs.getLong("qty_available") },
                buffetId, productId
        );

        int remaining = qty;
        List<BatchTake> takes = new ArrayList<>();

        for (long[] r : rows) {
            if (remaining == 0) break;

            long batchId = r[0];
            int avail = (int) r[1];
            int take = Math.min(avail, remaining);

            int updated = jdbc.update(
                    """
                    UPDATE inventory_batches
                    SET qty_available = qty_available - ?,
                        status = CASE WHEN (qty_available - ?) <= 0 THEN 'closed' ELSE status END
                    WHERE id=? AND qty_available >= ?
                    """,
                    take, take, batchId, take
            );

            if (updated != 1) {
                throw new IllegalStateException("Не удалось списать из партии id=" + batchId + " (конкурентное изменение?)");
            }

            // если партия стала closed — выключаем batch-discount
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
                    batchId, batchId
            );

            takes.add(new BatchTake(batchId, take));
            remaining -= take;
        }

        if (remaining != 0) {
            throw new IllegalStateException("consumeFromBatches: internal error, remaining=" + remaining);
        }

        return takes;
    }
    @Transactional(readOnly = true)
    public Map<Long, Integer> loadDiscountedQtyByProduct(long buffetId, List<Long> productIds) {
        if (productIds == null || productIds.isEmpty()) return Map.of();

        String inSql = productIds.stream().map(x -> "?").collect(Collectors.joining(","));

        String sql = """
        SELECT b.product_id, SUM(b.qty_available) AS qty
        FROM inventory_batches b
        JOIN batch_discounts bd
          ON bd.batch_id = b.id
         AND bd.is_active = TRUE
        WHERE b.buffet_id = ?
          AND b.status = 'active'
          AND b.qty_available > 0
          AND b.product_id IN (%s)
        GROUP BY b.product_id
        """.formatted(inSql);

        Object[] args = new Object[1 + productIds.size()];
        args[0] = buffetId;
        for (int i = 0; i < productIds.size(); i++) args[i + 1] = productIds.get(i);

        Map<Long, Integer> map = new HashMap<>();
        jdbc.query(sql, args, (rs) -> {
            map.put(rs.getLong("product_id"), rs.getInt("qty"));
        });
        return map;
    }
}
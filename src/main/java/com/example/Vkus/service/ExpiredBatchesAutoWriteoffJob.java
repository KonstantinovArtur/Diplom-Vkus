package com.example.Vkus.service;

import com.example.Vkus.service.AuditLogService;
import com.example.Vkus.service.WriteoffService;
import com.example.Vkus.web.dto.WriteoffCreateForm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ExpiredBatchesAutoWriteoffJob {

    private final JdbcTemplate jdbc;
    private final WriteoffService writeoffService;
    private final AuditLogService audit;
    private final TransactionTemplate tx;

    @Value("${vkus.jobs.expired-writeoff.enabled:true}")
    private boolean enabled;

    // ВАЖНО: этот пользователь должен реально существовать в users (FK в writeoff_docs.created_by)
    @Value("${vkus.jobs.expired-writeoff.system-user-id:1}")
    private long systemUserId;

    @Value("${vkus.jobs.expired-writeoff.zone:Europe/Madrid}")
    private String zone;

    public ExpiredBatchesAutoWriteoffJob(JdbcTemplate jdbc,
                                         WriteoffService writeoffService,
                                         AuditLogService audit,
                                         PlatformTransactionManager tm) {
        this.jdbc = jdbc;
        this.writeoffService = writeoffService;
        this.audit = audit;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * По умолчанию: каждый день в 01:10 (Madrid).
     * Важно: expires_at у тебя DATE. Я считаю, что "годен до 2026-03-02" => списываем с 2026-03-03,
     * поэтому условие expires_at < today.
     * Если хочешь списывать утром в сам день expires_at — поменяй < на <=.
     */
    @Scheduled(cron = "${vkus.jobs.expired-writeoff.cron:0 10 1 * * *}",
            zone = "${vkus.jobs.expired-writeoff.zone:Europe/Madrid}")
    public void run() {
        if (!enabled) return;

        LocalDate today = LocalDate.now(ZoneId.of(zone));

        while (true) {
            Boolean processedOne = tx.execute(status -> {
                List<BatchCandidate> rows = jdbc.query("""
                        SELECT id, buffet_id, product_id, qty_available, expires_at
                        FROM inventory_batches
                        WHERE status = 'active'
                          AND qty_available > 0
                          AND expires_at IS NOT NULL
                          AND expires_at < ?
                        ORDER BY expires_at ASC, received_at ASC, id ASC
                        FOR UPDATE SKIP LOCKED
                        LIMIT 1
                        """,
                        (rs, rn) -> new BatchCandidate(
                                rs.getLong("id"),
                                rs.getLong("buffet_id"),
                                rs.getLong("product_id"),
                                rs.getInt("qty_available"),
                                rs.getObject("expires_at", LocalDate.class)
                        ),
                        today
                );

                if (rows.isEmpty()) return false;

                BatchCandidate b = rows.get(0);
                if (b.qtyAvailable <= 0) return true; // на всякий случай

                WriteoffCreateForm f = new WriteoffCreateForm();
                f.setReason("expired");
                f.setProductId(b.productId);
                f.setBatchId(b.id);
                f.setQty(b.qtyAvailable);
                f.setComment("Автосписание: истёк срок годности (годен до " + b.expiresAt + ")");

                long docId = writeoffService.createWriteoff(b.buffetId, systemUserId, f);

                Map<String, Object> after = new LinkedHashMap<>();
                after.put("auto", true);
                after.put("docId", docId);
                after.put("buffetId", b.buffetId);
                after.put("actorUserId", systemUserId);
                after.put("productId", b.productId);
                after.put("batchId", b.id);
                after.put("qty", b.qtyAvailable);
                after.put("expiresAt", String.valueOf(b.expiresAt));

                // Чтобы в логах было видно, что это автосписание (и кто actor) — см. патч AuditLogService ниже
                audit.logAs(systemUserId, "WRITEOFF_CREATE", "writeoff", docId, Map.of("after", after));

                return true;
            });

            if (processedOne == null || !processedOne) break;
        }
    }

    private record BatchCandidate(long id, long buffetId, long productId, int qtyAvailable, LocalDate expiresAt) {}
}
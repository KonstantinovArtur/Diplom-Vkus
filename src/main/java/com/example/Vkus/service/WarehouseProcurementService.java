package com.example.Vkus.service;

import com.example.Vkus.entity.Invoice;
import com.example.Vkus.entity.InvoiceItem;
import com.example.Vkus.entity.Product;
import com.example.Vkus.entity.Supplier;
import com.example.Vkus.entity.SupplierPrice;
import com.example.Vkus.repository.InvoiceItemRepository;
import com.example.Vkus.repository.InvoiceRepository;
import com.example.Vkus.repository.ProductRepository;
import com.example.Vkus.repository.SupplierPriceRepository;
import com.example.Vkus.repository.SupplierRepository;
import com.example.Vkus.web.dto.InvoiceCreateForm;
import com.example.Vkus.web.dto.InvoiceItemAddForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class WarehouseProcurementService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceItemRepository invoiceItemRepository;
    private final SupplierRepository supplierRepository;
    private final ProductRepository productRepository;
    private final SupplierPriceRepository supplierPriceRepository;
    private final JdbcTemplate jdbcTemplate;

    public WarehouseProcurementService(InvoiceRepository invoiceRepository,
                                       InvoiceItemRepository invoiceItemRepository,
                                       SupplierRepository supplierRepository,
                                       ProductRepository productRepository,
                                       SupplierPriceRepository supplierPriceRepository,
                                       JdbcTemplate jdbcTemplate) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceItemRepository = invoiceItemRepository;
        this.supplierRepository = supplierRepository;
        this.productRepository = productRepository;
        this.supplierPriceRepository = supplierPriceRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public Invoice createInvoice(Long buffetId, Long createdBy, InvoiceCreateForm form) {
        return createInvoice(buffetId, form.getSupplierId(), createdBy);
    }

    private String generateInvoiceNumber(Long supplierId) {
        int year = LocalDate.now().getYear();
        long seq = invoiceRepository.countBySupplier_Id(supplierId) + 1;
        return String.format("SUP-%d-%d-%04d", supplierId, year, seq);
    }

    @Transactional
    public Invoice createInvoice(Long buffetId, Long supplierId, Long createdBy) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalStateException("Поставщик не найден"));

        Invoice inv = new Invoice();
        inv.setBuffetId(buffetId);
        inv.setSupplier(supplier);
        inv.setInvoiceNumber(generateInvoiceNumber(supplierId));
        inv.setCreatedBy(createdBy);

        if (inv.getStatus() == null) {
            inv.setStatus("draft");
        }

        return invoiceRepository.save(inv);
    }

    @Transactional
    public Invoice createInvoice(Long buffetId, Long supplierId, String invoiceNumber, LocalDate invoiceDate, Long createdBy) {
        Supplier supplier = supplierRepository.findById(supplierId)
                .orElseThrow(() -> new IllegalStateException("Поставщик не найден"));

        Invoice inv = new Invoice();
        inv.setBuffetId(buffetId);
        inv.setSupplier(supplier);
        inv.setInvoiceNumber(invoiceNumber);
        inv.setInvoiceDate(invoiceDate != null ? invoiceDate : LocalDate.now());
        inv.setCreatedBy(createdBy);

        if (inv.getStatus() == null) {
            inv.setStatus("draft");
        }

        return invoiceRepository.save(inv);
    }

    @Transactional
    public void addItem(Long invoiceId, InvoiceItemAddForm form) {
        Invoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        if (!"draft".equals(inv.getStatus())) {
            throw new IllegalStateException("Редактировать можно только накладную в статусе draft");
        }

        Product product = productRepository.findById(form.getProductId())
                .orElseThrow(() -> new IllegalStateException("Товар не найден"));

        SupplierPrice supplierPrice = supplierPriceRepository
                .findBySupplier_IdAndProduct_Id(inv.getSupplier().getId(), product.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Для выбранного товара отсутствует цена в прайсе поставщика"));

        InvoiceItem it = new InvoiceItem();
        it.setInvoice(inv);
        it.setProduct(product);
        it.setQty(form.getQty());
        it.setUnitPrice(supplierPrice.getPrice());
        it.setExpiresAt(form.getExpiresAt());

        invoiceItemRepository.save(it);
    }

    @Transactional
    public void deleteItem(Long itemId) {
        InvoiceItem it = invoiceItemRepository.findById(itemId)
                .orElseThrow(() -> new IllegalStateException("Позиция накладной не найдена"));

        Invoice inv = it.getInvoice();
        if (inv != null && !"draft".equals(inv.getStatus())) {
            throw new IllegalStateException("Нельзя изменять накладную, если она не в статусе draft");
        }

        invoiceItemRepository.delete(it);
    }

    @Transactional
    public void sendToSellerCheck(Long invoiceId, Long currentBuffetId) {
        Invoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        if (!currentBuffetId.equals(inv.getBuffetId())) {
            throw new IllegalStateException("Нет доступа к накладной другого буфета");
        }

        if (!"draft".equals(inv.getStatus())) {
            throw new IllegalStateException("Отправить на проверку можно только накладную в статусе draft");
        }

        List<InvoiceItem> items = invoiceItemRepository.findByInvoice_IdOrderByIdAsc(invoiceId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Нельзя отправить на проверку пустую накладную");
        }

        inv.setStatus("ready_for_check");
        invoiceRepository.save(inv);
    }

    @Transactional
    public void postInvoice(Long invoiceId, Long currentBuffetId, Long actorUserId) {
        Invoice inv = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new IllegalStateException("Накладная не найдена"));

        if (!currentBuffetId.equals(inv.getBuffetId())) {
            throw new IllegalStateException("Нет доступа к накладной другого буфета");
        }

        String status = inv.getStatus();

        if (!"checked".equals(status)) {
            throw new IllegalStateException(
                    "Проведение возможно только после проверки продавцом (status=checked). Текущий статус: " + status
            );
        }

        List<InvoiceItem> items = invoiceItemRepository.findByInvoice_IdOrderByIdAsc(invoiceId);
        if (items.isEmpty()) {
            throw new IllegalStateException("Нельзя провести пустую накладную");
        }

        Long buffetId = inv.getBuffetId();

        for (InvoiceItem it : items) {
            Long productId = it.getProduct().getId();

            Integer receivedQty = it.getReceivedQty();
            int accepted = (receivedQty != null) ? receivedQty : it.getQty();

            if (accepted <= 0) {
                continue;
            }

            Long batchId = insertBatch(
                    buffetId,
                    productId,
                    accepted,
                    it.getExpiresAt()
            );

            upsertInventoryItem(buffetId, productId, accepted);
            insertMovement(buffetId, productId, batchId, accepted, actorUserId, invoiceId);
        }

        inv.setStatus("posted");
        inv.setPostedAt(LocalDateTime.now());
        inv.setPostedBy(actorUserId);
        invoiceRepository.save(inv);
    }

    private Long insertBatch(Long buffetId,
                             Long productId,
                             int qty,
                             LocalDate expiresAt) {
        KeyHolder kh = new GeneratedKeyHolder();

        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(
                    """
                    INSERT INTO inventory_batches
                    (buffet_id, product_id, received_at, expires_at,
                     qty_received, qty_available, status)
                    VALUES (?, ?, now(), ?, ?, ?, 'active')
                    """,
                    new String[]{"id"}
            );

            ps.setLong(1, buffetId);
            ps.setLong(2, productId);

            if (expiresAt == null) {
                ps.setObject(3, null);
            } else {
                ps.setObject(3, expiresAt);
            }

            ps.setInt(4, qty);
            ps.setInt(5, qty);

            return ps;
        }, kh);

        Number key = kh.getKey();
        if (key == null) {
            throw new IllegalStateException("Не удалось создать партию");
        }
        return key.longValue();
    }

    private void upsertInventoryItem(Long buffetId, Long productId, int qty) {
        jdbcTemplate.update(
                """
                INSERT INTO inventory_items (buffet_id, product_id, quantity, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (buffet_id, product_id)
                DO UPDATE SET
                    quantity = inventory_items.quantity + EXCLUDED.quantity,
                    updated_at = now()
                """,
                buffetId, productId, qty
        );
    }

    private void insertMovement(Long buffetId, Long productId, Long batchId, int qty, Long actorUserId, Long invoiceId) {
        jdbcTemplate.update(
                """
                INSERT INTO inventory_movements
                (buffet_id, product_id, batch_id, type, qty, created_at, created_by, ref_type, ref_id)
                VALUES (?, ?, ?, 'receipt', ?, now(), ?, 'invoice', ?)
                """,
                buffetId, productId, batchId, qty, actorUserId, invoiceId
        );
    }
}
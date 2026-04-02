package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "combo_slot_products")
public class ComboSlotProduct {

    @EmbeddedId
    private ComboSlotProductId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("comboSlotId")
    @JoinColumn(name = "combo_slot_id", nullable = false)
    private ComboSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("productId")
    @JoinColumn(name = "product_id", nullable = false)
    private Product product; // твоя сущность уже есть :contentReference[oaicite:4]{index=4}

    @Column(name = "extra_price", precision = 10, scale = 2)
    private BigDecimal extraPrice; // может быть null

    public ComboSlotProduct() {}

    public ComboSlotProduct(ComboSlot slot, Product product) {
        this.slot = slot;
        this.product = product;
        this.id = new ComboSlotProductId(slot.getId(), product.getId());
    }

    @PrePersist
    public void prePersist() {
        if (id == null && slot != null && product != null) {
            id = new ComboSlotProductId(slot.getId(), product.getId());
        }
        // extraPrice может быть null — ок
    }

    // ===== getters/setters =====
    public ComboSlotProductId getId() { return id; }
    public void setId(ComboSlotProductId id) { this.id = id; }

    public ComboSlot getSlot() { return slot; }
    public void setSlot(ComboSlot slot) { this.slot = slot; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public BigDecimal getExtraPrice() { return extraPrice; }
    public void setExtraPrice(BigDecimal extraPrice) { this.extraPrice = extraPrice; }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComboSlotProduct that)) return false;
        return java.util.Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(id);
    }
}
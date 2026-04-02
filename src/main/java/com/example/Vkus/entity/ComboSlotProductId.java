package com.example.Vkus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ComboSlotProductId implements Serializable {

    @Column(name = "combo_slot_id")
    private Long comboSlotId;

    @Column(name = "product_id")
    private Long productId;

    public ComboSlotProductId() {}

    public ComboSlotProductId(Long comboSlotId, Long productId) {
        this.comboSlotId = comboSlotId;
        this.productId = productId;
    }

    public Long getComboSlotId() { return comboSlotId; }
    public void setComboSlotId(Long comboSlotId) { this.comboSlotId = comboSlotId; }

    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComboSlotProductId that)) return false;
        return Objects.equals(comboSlotId, that.comboSlotId) && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(comboSlotId, productId);
    }
}
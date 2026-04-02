package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name = "cart_combo_items")
public class CartComboItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_combo_id", nullable = false)
    private CartCombo cartCombo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_slot_id", nullable = false)
    private ComboSlot comboSlot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "qty", nullable = false)
    private Integer qty = 1;

    @Column(name = "extra_price_snapshot", precision = 10, scale = 2)
    private BigDecimal extraPriceSnapshot; // может быть null

    @PrePersist
    void prePersist() {
        if (qty == null || qty <= 0) qty = 1;
    }

    // ===== getters/setters =====
    public Long getId() { return id; }

    public CartCombo getCartCombo() { return cartCombo; }
    public void setCartCombo(CartCombo cartCombo) { this.cartCombo = cartCombo; }

    public ComboSlot getComboSlot() { return comboSlot; }
    public void setComboSlot(ComboSlot comboSlot) { this.comboSlot = comboSlot; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public BigDecimal getExtraPriceSnapshot() { return extraPriceSnapshot; }
    public void setExtraPriceSnapshot(BigDecimal extraPriceSnapshot) { this.extraPriceSnapshot = extraPriceSnapshot; }
}
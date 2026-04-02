package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "cart_combos")
public class CartCombo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_template_id", nullable = false)
    private ComboTemplate comboTemplate;

    @Column(name = "qty", nullable = false)
    private Integer qty = 1;

    @Column(name = "combo_price_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal comboPriceSnapshot = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "cartCombo", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CartComboItem> items = new ArrayList<>();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (qty == null || qty <= 0) qty = 1;
        if (comboPriceSnapshot == null) comboPriceSnapshot = BigDecimal.ZERO;
    }

    // ===== getters/setters =====
    public Long getId() { return id; }

    public Cart getCart() { return cart; }
    public void setCart(Cart cart) { this.cart = cart; }

    public ComboTemplate getComboTemplate() { return comboTemplate; }
    public void setComboTemplate(ComboTemplate comboTemplate) { this.comboTemplate = comboTemplate; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }

    public BigDecimal getComboPriceSnapshot() { return comboPriceSnapshot; }
    public void setComboPriceSnapshot(BigDecimal comboPriceSnapshot) { this.comboPriceSnapshot = comboPriceSnapshot; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public List<CartComboItem> getItems() { return items; }
    public void setItems(List<CartComboItem> items) { this.items = items; }
}
package com.example.Vkus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "inventory_items",
        uniqueConstraints = @UniqueConstraint(name = "uq_inventory_items_buffet_product", columnNames = {"buffet_id", "product_id"})
)
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buffet_id", nullable = false,
            foreignKey = @ForeignKey(name = "inventory_items_buffet_id_fkey"))
    private Buffet buffet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false,
            foreignKey = @ForeignKey(name = "inventory_items_product_id_fkey"))
    private Product product;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();







    @PrePersist
    protected void onCreate() {
        if (updatedAt == null) updatedAt = LocalDateTime.now();
        if (quantity == null) quantity = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ===== getters/setters =====

    public Long getId() { return id; }



    public Buffet getBuffet() { return buffet; }
    public void setBuffet(Buffet buffet) { this.buffet = buffet; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}

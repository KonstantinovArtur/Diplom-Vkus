package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "product_discounts")
public class ProductDiscount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="buffet_id", nullable = false)
    private Long buffetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="product_id", nullable = false)
    private Product product;

    @Column(name="percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal percent;

    @Column(name="start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name="end_at")
    private LocalDateTime endAt;

    @Column(name="is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name="created_by", nullable = false)
    private Long createdBy;

    @Column(name="created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (startAt == null) startAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
    }

    // getters/setters
    public Long getId() { return id; }

    public Long getBuffetId() { return buffetId; }
    public void setBuffetId(Long buffetId) { this.buffetId = buffetId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }

    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }

    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

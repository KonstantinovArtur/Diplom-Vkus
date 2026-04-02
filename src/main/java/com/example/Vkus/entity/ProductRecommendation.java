package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "product_recommendations",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_product_recommendations_unique",
                        columnNames = {"buffet_id", "product_id", "recommended_product_id"}
                )
        }
)
public class ProductRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "buffet_id", nullable = false)
    private Long buffetId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "recommended_product_id", nullable = false)
    private Product recommendedProduct;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (sortOrder == null) sortOrder = 0;
        if (isActive == null) isActive = true;
    }

    public Long getId() { return id; }

    public Long getBuffetId() { return buffetId; }
    public void setBuffetId(Long buffetId) { this.buffetId = buffetId; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }

    public Product getRecommendedProduct() { return recommendedProduct; }
    public void setRecommendedProduct(Product recommendedProduct) { this.recommendedProduct = recommendedProduct; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
@Table(name="monthly_discount_offer_items",
        uniqueConstraints = @UniqueConstraint(columnNames = {"offer_id","category_id"}))
public class MonthlyDiscountOfferItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="offer_id", nullable=false)
    private MonthlyDiscountOffer offer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="category_id", nullable=false)
    private Category category;

    @Column(name="percent", nullable=false, precision=5, scale=2)
    private BigDecimal percent;

    // getters/setters
    public Long getId() { return id; }
    public MonthlyDiscountOffer getOffer() { return offer; }
    public void setOffer(MonthlyDiscountOffer offer) { this.offer = offer; }
    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }
    public BigDecimal getPercent() { return percent; }
    public void setPercent(BigDecimal percent) { this.percent = percent; }
}

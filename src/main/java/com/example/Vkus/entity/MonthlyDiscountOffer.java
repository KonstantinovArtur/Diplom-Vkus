package com.example.Vkus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "monthly_discount_offers",
        uniqueConstraints = @UniqueConstraint(columnNames = {"buffet_id","year","month"}))
public class MonthlyDiscountOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="buffet_id", nullable=false)
    private Long buffetId;

    @Column(name="year", nullable=false)
    private Integer year;

    @Column(name="month", nullable=false)
    private Integer month;

    @Column(name="created_at", nullable=false)
    private LocalDateTime createdAt;

    @Column(name="created_by", nullable=false)
    private Long createdBy;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    // getters/setters
    public Long getId() { return id; }
    public Long getBuffetId() { return buffetId; }
    public void setBuffetId(Long buffetId) { this.buffetId = buffetId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}

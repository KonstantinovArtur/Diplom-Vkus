package com.example.Vkus.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "user_monthly_category_choices",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id","buffet_id","year","month"}))
public class UserMonthlyCategoryChoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name="user_id", nullable=false)
    private Long userId;

    @Column(name="buffet_id", nullable=false)
    private Long buffetId;

    @Column(name="year", nullable=false)
    private Integer year;

    @Column(name="month", nullable=false)
    private Integer month;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name="offer_item_id", nullable=false)
    private MonthlyDiscountOfferItem offerItem;

    @Column(name="chosen_at", nullable=false)
    private LocalDateTime chosenAt;

    @PrePersist
    void prePersist() {
        if (chosenAt == null) chosenAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getBuffetId() { return buffetId; }
    public void setBuffetId(Long buffetId) { this.buffetId = buffetId; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public Integer getMonth() { return month; }
    public void setMonth(Integer month) { this.month = month; }
    public MonthlyDiscountOfferItem getOfferItem() { return offerItem; }
    public void setOfferItem(MonthlyDiscountOfferItem offerItem) { this.offerItem = offerItem; }
    public LocalDateTime getChosenAt() { return chosenAt; }
    public void setChosenAt(LocalDateTime chosenAt) { this.chosenAt = chosenAt; }
}

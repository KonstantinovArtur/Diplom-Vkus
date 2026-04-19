package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders",
        indexes = {
                @Index(name = "ix_orders_status", columnList = "buffet_id,status,created_at")
        })
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buffet_id", nullable = false)
    private Buffet buffet;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "status", nullable = false)
    private String status = "created";

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(name = "final_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal finalAmount = BigDecimal.ZERO;

    @Column(name = "pickup_code", nullable = false)
    private String pickupCode;

    @Column(name = "pickup_code_expires_at")
    private LocalDateTime pickupCodeExpiresAt;

    @Column(name = "issued_at")
    private LocalDateTime issuedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id")
    private User seller;

    @Column(name = "buyer_name_snapshot")
    private String buyerNameSnapshot;

    @Column(name = "buyer_email_snapshot")
    private String buyerEmailSnapshot;

    @Column(name = "buffet_name_snapshot")
    private String buffetNameSnapshot;

    @Column(name = "order_date", insertable = false, updatable = false)
    private LocalDate orderDate;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = "created";
        if (totalAmount == null) totalAmount = BigDecimal.ZERO;
        if (discountAmount == null) discountAmount = BigDecimal.ZERO;
        if (finalAmount == null) finalAmount = BigDecimal.ZERO;
    }

    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Buffet getBuffet() { return buffet; }
    public void setBuffet(Buffet buffet) { this.buffet = buffet; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getDiscountAmount() { return discountAmount; }
    public void setDiscountAmount(BigDecimal discountAmount) { this.discountAmount = discountAmount; }

    public BigDecimal getFinalAmount() { return finalAmount; }
    public void setFinalAmount(BigDecimal finalAmount) { this.finalAmount = finalAmount; }

    public String getPickupCode() { return pickupCode; }
    public void setPickupCode(String pickupCode) { this.pickupCode = pickupCode; }

    public LocalDateTime getPickupCodeExpiresAt() { return pickupCodeExpiresAt; }
    public void setPickupCodeExpiresAt(LocalDateTime pickupCodeExpiresAt) { this.pickupCodeExpiresAt = pickupCodeExpiresAt; }

    public LocalDateTime getIssuedAt() { return issuedAt; }
    public void setIssuedAt(LocalDateTime issuedAt) { this.issuedAt = issuedAt; }

    public User getSeller() { return seller; }
    public void setSeller(User seller) { this.seller = seller; }

    public String getBuyerNameSnapshot() { return buyerNameSnapshot; }
    public void setBuyerNameSnapshot(String buyerNameSnapshot) { this.buyerNameSnapshot = buyerNameSnapshot; }

    public String getBuyerEmailSnapshot() { return buyerEmailSnapshot; }
    public void setBuyerEmailSnapshot(String buyerEmailSnapshot) { this.buyerEmailSnapshot = buyerEmailSnapshot; }

    public String getBuffetNameSnapshot() { return buffetNameSnapshot; }
    public void setBuffetNameSnapshot(String buffetNameSnapshot) { this.buffetNameSnapshot = buffetNameSnapshot; }

    public LocalDate getOrderDate() { return orderDate; }
}
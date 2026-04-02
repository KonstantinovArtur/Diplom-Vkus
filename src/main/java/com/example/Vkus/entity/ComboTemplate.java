package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(
        name = "combo_templates",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_combo_templates_buffet_name",
                columnNames = {"buffet_id", "name"}
        )
)
public class ComboTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buffet_id", nullable = false)
    private Buffet buffet;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "base_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice = BigDecimal.ZERO;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "comboTemplate", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ComboSlot> slots = new HashSet<>();


    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (isActive == null) isActive = true;
        if (basePrice == null) basePrice = BigDecimal.ZERO;
    }

    // ===== getters/setters =====
    public Long getId() { return id; }

    public Buffet getBuffet() { return buffet; }
    public void setBuffet(Buffet buffet) { this.buffet = buffet; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBasePrice() { return basePrice; }
    public void setBasePrice(BigDecimal basePrice) { this.basePrice = basePrice; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean active) { isActive = active; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public User getCreatedBy() { return createdBy; }
    public void setCreatedBy(User createdBy) { this.createdBy = createdBy; }

    public Set<ComboSlot> getSlots() { return slots; }
    public void setSlots(Set<ComboSlot> slots) { this.slots = slots; }
}
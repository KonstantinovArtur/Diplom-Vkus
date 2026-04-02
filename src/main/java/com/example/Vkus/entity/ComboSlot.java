package com.example.Vkus.entity;

import jakarta.persistence.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(
        name = "combo_slots",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_combo_slots_template_name",
                columnNames = {"combo_template_id", "name"}
        )
)
public class ComboSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "combo_template_id", nullable = false)
    private ComboTemplate comboTemplate;

    @Column(name = "name", nullable = false)
    private String name; // "Еда", "Напиток"

    @Column(name = "required_qty", nullable = false)
    private Integer requiredQty = 1;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    // ВАЖНО: Set вместо List -> Hibernate больше не считает это "bag"
    @OneToMany(mappedBy = "slot", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ComboSlotProduct> products = new HashSet<>();

    @PrePersist
    public void prePersist() {
        if (requiredQty == null || requiredQty <= 0) requiredQty = 1;
        if (sortOrder == null) sortOrder = 0;
    }

    // ===== getters/setters =====
    public Long getId() { return id; }

    public ComboTemplate getComboTemplate() { return comboTemplate; }
    public void setComboTemplate(ComboTemplate comboTemplate) { this.comboTemplate = comboTemplate; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getRequiredQty() { return requiredQty; }
    public void setRequiredQty(Integer requiredQty) { this.requiredQty = requiredQty; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public Set<ComboSlotProduct> getProducts() { return products; }
    public void setProducts(Set<ComboSlotProduct> products) { this.products = products; }
}
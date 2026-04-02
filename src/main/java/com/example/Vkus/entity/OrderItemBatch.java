package com.example.Vkus.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "order_item_batches")
public class OrderItemBatch {

    @EmbeddedId
    private Pk id = new Pk();

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("orderItemId")
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    // В БД это FK на inventory_batches(id), но entity нет — поэтому просто поле.
    @Column(name = "batch_id", nullable = false, insertable = false, updatable = false)
    private Long batchId;

    @Column(name = "qty", nullable = false)
    private Integer qty;

    @Embeddable
    public static class Pk implements java.io.Serializable {

        @Column(name = "order_item_id")
        private Long orderItemId;

        @Column(name = "batch_id")
        private Long batchId;

        public Long getOrderItemId() { return orderItemId; }
        public void setOrderItemId(Long orderItemId) { this.orderItemId = orderItemId; }

        public Long getBatchId() { return batchId; }
        public void setBatchId(Long batchId) { this.batchId = batchId; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return java.util.Objects.equals(orderItemId, pk.orderItemId)
                    && java.util.Objects.equals(batchId, pk.batchId);
        }

        @Override
        public int hashCode() {
            return java.util.Objects.hash(orderItemId, batchId);
        }
    }

    public Pk getId() { return id; }
    public void setId(Pk id) { this.id = id; }

    public OrderItem getOrderItem() { return orderItem; }
    public void setOrderItem(OrderItem orderItem) { this.orderItem = orderItem; }

    public Long getBatchId() { return batchId; }

    public Integer getQty() { return qty; }
    public void setQty(Integer qty) { this.qty = qty; }
}

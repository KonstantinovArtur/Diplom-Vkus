package com.example.Vkus.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.ArrayList;
import java.util.List;

public class SellerInvoiceCheckForm {

    @NotNull
    private Long invoiceId;

    @Valid
    private List<Item> items = new ArrayList<>();

    public Long getInvoiceId() { return invoiceId; }
    public void setInvoiceId(Long invoiceId) { this.invoiceId = invoiceId; }

    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }

    public static class Item {
        @NotNull
        private Long invoiceItemId;

        @NotNull
        private Integer receivedQty; // факт

        private String acceptanceReason; // ok / defect / shortage / missing / quantity_error

        public Long getInvoiceItemId() { return invoiceItemId; }
        public void setInvoiceItemId(Long invoiceItemId) { this.invoiceItemId = invoiceItemId; }

        public Integer getReceivedQty() { return receivedQty; }
        public void setReceivedQty(Integer receivedQty) { this.receivedQty = receivedQty; }

        public String getAcceptanceReason() { return acceptanceReason; }
        public void setAcceptanceReason(String acceptanceReason) { this.acceptanceReason = acceptanceReason; }
    }
}
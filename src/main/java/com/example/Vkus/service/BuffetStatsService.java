package com.example.Vkus.service;

import com.example.Vkus.repository.InvoiceItemRepository;
import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.web.dto.DailyProfitabilityRow;
import com.example.Vkus.web.dto.DailySalesRow;
import com.example.Vkus.web.dto.TopProductRow;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class BuffetStatsService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InvoiceItemRepository invoiceItemRepository;

    private static final List<String> SOLD_STATUSES = List.of("issued");

    public BuffetStatsService(OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              InvoiceItemRepository invoiceItemRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.invoiceItemRepository = invoiceItemRepository;
    }

    public StatsResult getStats(Long buffetId, LocalDate from, LocalDate to) {
        List<DailySalesRow> daily = orderRepository.findDailySales(buffetId, from, to, SOLD_STATUSES);
        List<TopProductRow> top = orderItemRepository.findTopProducts(buffetId, from, to, SOLD_STATUSES);
        List<DailyProfitabilityRow> profitability = invoiceItemRepository.findDailyProfitability(buffetId, from, to);

        long ordersTotal = daily.stream()
                .mapToLong(x -> x.getOrdersCount() == null ? 0L : x.getOrdersCount())
                .sum();

        BigDecimal revenueTotal = profitability.stream()
                .map(DailyProfitabilityRow::getRevenue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal costsTotal = profitability.stream()
                .map(DailyProfitabilityRow::getCosts)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal profitTotal = profitability.stream()
                .map(DailyProfitabilityRow::getProfit)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new StatsResult(
                from,
                to,
                ordersTotal,
                revenueTotal,
                costsTotal,
                profitTotal,
                daily,
                profitability,
                top
        );
    }

    public record StatsResult(
            LocalDate from,
            LocalDate to,
            long ordersTotal,
            BigDecimal revenueTotal,
            BigDecimal costsTotal,
            BigDecimal profitTotal,
            List<DailySalesRow> daily,
            List<DailyProfitabilityRow> profitability,
            List<TopProductRow> topProducts
    ) {}
}
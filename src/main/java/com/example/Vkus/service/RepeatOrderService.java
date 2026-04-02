package com.example.Vkus.service;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.OrderItem;
import com.example.Vkus.repository.OrderItemRepository;
import com.example.Vkus.repository.OrderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class RepeatOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CartService cartService;

    public RepeatOrderService(OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              CartService cartService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.cartService = cartService;
    }

    @Transactional
    public RepeatResult repeat(Long userId, Long buffetId, Long orderId) {

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        // безопасность: только свой заказ
        if (order.getUser() == null || !order.getUser().getId().equals(userId)) {
            throw new RuntimeException("Forbidden: not your order");
        }

        // безопасность: повторяем только в рамках текущего буфета
        if (order.getBuffet() == null || !order.getBuffet().getId().equals(buffetId)) {
            throw new RuntimeException("Forbidden: order belongs to another buffet");
        }

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);

        int addedPositions = 0;
        List<Long> skippedProductIds = new ArrayList<>();

        for (OrderItem it : items) {
            if (it.getProduct() == null) continue;

            Long productId = it.getProduct().getId();
            int qty = it.getQty() != null ? it.getQty() : 0;

            if (productId == null || qty <= 0) continue;

            try {
                cartService.add(userId, buffetId, productId, qty); // ваш метод :contentReference[oaicite:3]{index=3}
                addedPositions++;
            } catch (Exception ex) {
                // если товар удалён/не найден -> CartService кинет исключение
                skippedProductIds.add(productId);
            }
        }

        return new RepeatResult(addedPositions, skippedProductIds);
    }

    public record RepeatResult(int addedPositions, List<Long> skippedProductIds) {}
}
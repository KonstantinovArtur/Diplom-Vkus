package com.example.Vkus.service;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.Payment;
import com.example.Vkus.repository.OrderRepository;
import com.example.Vkus.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class StubPaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    public StubPaymentService(PaymentRepository paymentRepository, OrderRepository orderRepository) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
    }

    @Transactional
    public Payment payOrderStub(Long orderId) {
        Order order = orderRepository.findById(orderId).orElseThrow();

        Payment p = paymentRepository.findTopByOrderIdOrderByIdDesc(order.getId())
                .orElseThrow(() -> new IllegalStateException("Payment row not found for orderId=" + orderId));

        // уже оплачено — просто возвращаем
        if ("succeeded".equalsIgnoreCase(p.getStatus())) {
            return p;
        }
        if (!"pending".equalsIgnoreCase(p.getStatus())) {
            throw new IllegalStateException("Payment is not pending: " + p.getStatus());
        }

        // Заглушка “успешной оплаты”
        p.setStatus("succeeded");
        p.setPaidAt(LocalDateTime.now());
        p.setProviderPaymentId("stub-" + UUID.randomUUID());
        // p.setProviderPayloadJson("{\"stub\":true}"); // если у тебя поле String/JSONB и есть сеттер

        return paymentRepository.save(p);
    }
}

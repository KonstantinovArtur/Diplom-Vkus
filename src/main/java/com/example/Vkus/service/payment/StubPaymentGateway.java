package com.example.Vkus.service.payment;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.Payment;
import com.example.Vkus.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class StubPaymentGateway implements PaymentGateway {

    private final PaymentRepository paymentRepository;

    public StubPaymentGateway(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    @Transactional
    public Payment createAndPay(Order order) {
        Payment p = new Payment();
        p.setOrder(order);
        p.setProvider("stub");
        p.setStatus("succeeded"); // заглушка “оплачено”
        p.setAmount(order.getFinalAmount());
        p.setCurrency("RUB");
        p.setPaidAt(LocalDateTime.now());
        return paymentRepository.save(p);
    }
}

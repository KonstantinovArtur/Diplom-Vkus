package com.example.Vkus.service.payment;

import com.example.Vkus.entity.Order;
import com.example.Vkus.entity.Payment;

public interface PaymentGateway {
    Payment createAndPay(Order order);
}

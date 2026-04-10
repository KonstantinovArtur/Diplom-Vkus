package com.example.Vkus.service;

import com.example.Vkus.entity.BuyerNotification;
import com.example.Vkus.entity.Order;
import com.example.Vkus.repository.BuyerNotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BuyerNotificationService {

    private final BuyerNotificationRepository buyerNotificationRepository;

    public BuyerNotificationService(BuyerNotificationRepository buyerNotificationRepository) {
        this.buyerNotificationRepository = buyerNotificationRepository;
    }

    @Transactional
    public void createOrderReadyNotification(Order order) {
        if (order == null || order.getId() == null || order.getUser() == null) {
            return;
        }

        Long userId = order.getUser().getId();
        Long orderId = order.getId();

        boolean exists = buyerNotificationRepository
                .existsByUserIdAndOrderIdAndType(userId, orderId, "order_ready");

        if (exists) {
            return;
        }

        BuyerNotification n = new BuyerNotification();
        n.setUser(order.getUser());
        n.setOrder(order);
        n.setBuffet(order.getBuffet());
        n.setType("order_ready");
        n.setTitle("Заказ готов");
        n.setMessage("Заказ #" + order.getId() + " готов к выдаче.");
        n.setRead(false);

        buyerNotificationRepository.save(n);
    }

    @Transactional(readOnly = true)
    public List<BuyerNotification> getLatestForUser(Long userId) {
        if (userId == null) {
            return List.of();
        }
        return buyerNotificationRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        if (userId == null) {
            return 0;
        }
        return buyerNotificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        return buyerNotificationRepository.markAllAsRead(userId, LocalDateTime.now());
    }

    @Transactional
    public int clearRead(Long userId) {
        if (userId == null) {
            return 0;
        }
        return buyerNotificationRepository.deleteAllReadByUserId(userId);
    }
}
package com.example.Vkus.repository;

import com.example.Vkus.entity.BuyerNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface BuyerNotificationRepository extends JpaRepository<BuyerNotification, Long> {

    List<BuyerNotification> findTop10ByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserIdAndIsReadFalse(Long userId);

    boolean existsByUserIdAndOrderIdAndType(Long userId, Long orderId, String type);

    @Modifying
    @Query("""
        update BuyerNotification n
           set n.isRead = true,
               n.readAt = :readAt
         where n.user.id = :userId
           and n.isRead = false
    """)
    int markAllAsRead(Long userId, LocalDateTime readAt);

    @Modifying
    @Query("""
        delete from BuyerNotification n
         where n.user.id = :userId
           and n.isRead = true
    """)
    int deleteAllReadByUserId(Long userId);
}
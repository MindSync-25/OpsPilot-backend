package com.itops.repository;

import com.itops.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    
    // Find all notifications for a company
    List<Notification> findByCompanyId(UUID companyId);
    
    // Find all notifications for a user (most recent first)
    List<Notification> findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    
    // Find unread notifications for a user
    List<Notification> findByUserIdAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(UUID userId);
    
    // Count unread notifications for a user
    long countByUserIdAndIsReadFalseAndDeletedAtIsNull(UUID userId);
    
    // Find notifications by entity
    List<Notification> findByEntityTypeAndEntityIdAndDeletedAtIsNull(String entityType, UUID entityId);
    
    // Mark notification as read
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.id = :notificationId AND n.user.id = :userId")
    int markAsRead(@Param("notificationId") UUID notificationId, @Param("userId") UUID userId);
    
    // Mark all notifications as read for a user
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsRead(@Param("userId") UUID userId);
    
    // Delete old read notifications (for cleanup)
    @Modifying
    @Query("UPDATE Notification n SET n.deletedAt = CURRENT_TIMESTAMP WHERE n.user.id = :userId AND n.isRead = true AND n.createdAt < :beforeDate")
    int deleteOldReadNotifications(@Param("userId") UUID userId, @Param("beforeDate") java.time.LocalDateTime beforeDate);
}

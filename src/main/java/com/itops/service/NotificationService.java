package com.itops.service;

import com.itops.domain.Notification;
import com.itops.domain.User;
import com.itops.dto.NotificationResponse;
import com.itops.repository.NotificationRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    
    /**
     * Create a notification for a specific user
     */
    @Transactional
    public Notification createNotification(
            UUID userId,
            UUID companyId,
            String type,
            String title,
            String message,
            String entityType,
            UUID entityId,
            UUID actorId
    ) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found: " + userId));
            
            User actor = actorId != null ? userRepository.findById(actorId).orElse(null) : null;
            
            // Don't notify the actor about their own action
            if (actorId != null && actorId.equals(userId)) {
                log.debug("Skipping self-notification for user: {}", userId);
                return null;
            }
            
            Notification notification = Notification.builder()
                    .user(user)
                    .type(type)
                    .title(title)
                    .message(message)
                    .entityType(entityType)
                    .entityId(entityId)
                    .actor(actor)
                    .isRead(false)
                    .build();
            
            notification.setCompanyId(companyId);
            
            return notificationRepository.save(notification);
        } catch (Exception e) {
            log.error("Error creating notification for user {}: {}", userId, e.getMessage());
            return null; // Don't fail the main operation if notification fails
        }
    }
    
    /**
     * Create notifications for multiple users
     */
    @Transactional
    public void createNotifications(
            List<UUID> userIds,
            UUID companyId,
            String type,
            String title,
            String message,
            String entityType,
            UUID entityId,
            UUID actorId
    ) {
        userIds.forEach(userId -> 
            createNotification(userId, companyId, type, title, message, entityType, entityId, actorId)
        );
    }
    
    /**
     * Get all notifications for a user
     */
    public List<NotificationResponse> getUserNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    
    /**
     * Get unread notifications for a user
     */
    public List<NotificationResponse> getUnreadNotifications(UUID userId) {
        return notificationRepository.findByUserIdAndIsReadFalseAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toResponse)
                .toList();
    }
    
    /**
     * Get unread count for a user
     */
    public long getUnreadCount(UUID userId) {
        return notificationRepository.countByUserIdAndIsReadFalseAndDeletedAtIsNull(userId);
    }
    
    /**
     * Mark notification as read
     */
    @Transactional
    public boolean markAsRead(UUID notificationId, UUID userId) {
        int updated = notificationRepository.markAsRead(notificationId, userId);
        return updated > 0;
    }
    
    /**
     * Mark all notifications as read for a user
     */
    @Transactional
    public int markAllAsRead(UUID userId) {
        return notificationRepository.markAllAsRead(userId);
    }
    
    /**
     * Delete notification (soft delete)
     */
    @Transactional
    public boolean deleteNotification(UUID notificationId, UUID userId) {
        return notificationRepository.findById(notificationId)
                .filter(n -> n.getUser().getId().equals(userId))
                .map(notification -> {
                    notification.setDeletedAt(LocalDateTime.now());
                    notificationRepository.save(notification);
                    return true;
                })
                .orElse(false);
    }
    
    /**
     * Cleanup old read notifications (older than 30 days)
     */
    @Transactional
    public int cleanupOldNotifications(UUID userId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        return notificationRepository.deleteOldReadNotifications(userId, thirtyDaysAgo);
    }
    
    /**
     * Convert Notification entity to NotificationResponse DTO
     */
    private NotificationResponse toResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUser() != null ? notification.getUser().getId() : null)
                .actorId(notification.getActor() != null ? notification.getActor().getId() : null)
                .actorName(notification.getActor() != null ? notification.getActor().getName() : null)
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .entityType(notification.getEntityType())
                .entityId(notification.getEntityId())
                .isRead(notification.getIsRead())
                .readAt(notification.getReadAt())
                .createdAt(notification.getCreatedAt())
                .updatedAt(notification.getUpdatedAt())
                .build();
    }
}

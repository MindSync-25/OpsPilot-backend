package com.itops.controller;

import com.itops.dto.NotificationResponse;
import com.itops.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class NotificationController {
    
    private final NotificationService notificationService;
    
    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getUserNotifications(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(notificationService.getUserNotifications(userId));
    }
    
    @GetMapping("/unread")
    public ResponseEntity<List<NotificationResponse>> getUnreadNotifications(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        return ResponseEntity.ok(notificationService.getUnreadNotifications(userId));
    }
    
    @GetMapping("/unread/count")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }
    
    @PutMapping("/{id}/read")
    public ResponseEntity<Map<String, Boolean>> markAsRead(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        boolean success = notificationService.markAsRead(id, userId);
        return ResponseEntity.ok(Map.of("success", success));
    }
    
    @PutMapping("/read-all")
    public ResponseEntity<Map<String, Integer>> markAllAsRead(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        int count = notificationService.markAllAsRead(userId);
        return ResponseEntity.ok(Map.of("count", count));
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNotification(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        notificationService.deleteNotification(id, userId);
        return ResponseEntity.noContent().build();
    }
    
    private UUID extractUserId(Authentication authentication) {
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) authentication.getPrincipal();
        return UUID.fromString((String) claims.get("userId"));
    }
}

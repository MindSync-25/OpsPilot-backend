package com.itops.service;

import com.itops.dto.CommentResponse;
import com.itops.dto.CreateCommentRequest;
import com.itops.dto.NotificationType;
import com.itops.model.Comment;
import com.itops.domain.Task;
import com.itops.domain.Subtask;
import com.itops.domain.User;
import com.itops.repository.CommentRepository;
import com.itops.repository.TaskRepository;
import com.itops.repository.SubtaskRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final TaskRepository taskRepository;
    private final SubtaskRepository subtaskRepository;
    
    // Pattern to match @mentions (e.g., @john@example.com or @username)
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}|[\\w]+)");

    @Transactional
    public CommentResponse createComment(CreateCommentRequest request, UUID companyId, UUID userId) {
        Comment comment = Comment.builder()
                .companyId(companyId)
                .entityType(request.getEntityType())
                .entityId(request.getEntityId())
                .userId(userId)
                .content(request.getContent())
                .build();

        Comment saved = commentRepository.save(comment);
        
        // Get entity name for notifications
        String entityName = "";
        String entityTypeStr = "";
        
        // Send notification based on entity type
        if (request.getEntityType() == Comment.EntityType.TASK) {
            Task task = taskRepository.findById(request.getEntityId()).orElse(null);
            if (task != null) {
                entityName = task.getTitle();
                entityTypeStr = "TASK";
                
                if (task.getAssignedTo() != null) {
                    notificationService.createNotification(
                            task.getAssignedTo(),
                            companyId,
                            NotificationType.COMMENT_ADDED,
                            "New Comment on Task",
                            "A comment was added to task: " + task.getTitle(),
                            "TASK",
                            task.getId(),
                            userId
                    );
                }
            }
        } else if (request.getEntityType() == Comment.EntityType.SUBTASK) {
            Subtask subtask = subtaskRepository.findById(request.getEntityId()).orElse(null);
            if (subtask != null) {
                entityName = subtask.getTitle();
                entityTypeStr = "SUBTASK";
                
                if (subtask.getAssignedTo() != null) {
                    notificationService.createNotification(
                            subtask.getAssignedTo(),
                            companyId,
                            NotificationType.COMMENT_ADDED,
                            "New Comment on Subtask",
                            "A comment was added to subtask: " + subtask.getTitle(),
                            "SUBTASK",
                            subtask.getId(),
                            userId
                    );
                }
            }
        }
        
        // Check for mentions and notify mentioned users
        List<User> mentionedUsers = extractMentionedUsers(request.getContent(), companyId);
        for (User mentionedUser : mentionedUsers) {
            // Don't notify the comment author
            if (!mentionedUser.getId().equals(userId)) {
                notificationService.createNotification(
                        mentionedUser.getId(),
                        companyId,
                        NotificationType.COMMENT_MENTION,
                        "You were mentioned in a comment",
                        "You were mentioned in a comment on " + entityTypeStr.toLowerCase() + ": " + entityName,
                        entityTypeStr,
                        request.getEntityId(),
                        userId
                );
            }
        }
        
        return mapToResponse(saved);
    }
    
    /**
     * Extract users mentioned in comment content (e.g., @username or @email)
     */
    private List<User> extractMentionedUsers(String content, UUID companyId) {
        List<User> mentionedUsers = new ArrayList<>();
        Set<String> uniqueMentions = new HashSet<>();
        
        Matcher matcher = MENTION_PATTERN.matcher(content);
        while (matcher.find()) {
            String mention = matcher.group(1);
            if (!uniqueMentions.contains(mention)) {
                uniqueMentions.add(mention);
                
                // Try to find user by email or name
                User user = null;
                if (mention.contains("@")) {
                    // It's an email
                    user = userRepository.findByEmailAndCompanyId(mention, companyId).orElse(null);
                } else {
                    // It's a username - try to find by name (case-insensitive)
                    user = userRepository.findByNameIgnoreCaseAndCompanyId(mention, companyId).orElse(null);
                }
                
                if (user != null) {
                    mentionedUsers.add(user);
                }
            }
        }
        
        return mentionedUsers;
    }

    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(
            Comment.EntityType entityType, 
            UUID entityId, 
            UUID companyId
    ) {
        List<Comment> comments = commentRepository
                .findByCompanyIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
                        companyId, 
                        entityType, 
                        entityId
                );
        
        return comments.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private CommentResponse mapToResponse(Comment comment) {
        User user = userRepository.findById(comment.getUserId())
                .orElse(null);
        
        return CommentResponse.builder()
                .id(comment.getId())
                .entityType(comment.getEntityType())
                .entityId(comment.getEntityId())
                .userId(comment.getUserId())
                .userName(user != null ? user.getName() : "Unknown")
                .userEmail(user != null ? user.getEmail() : "")
                .content(comment.getContent())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}

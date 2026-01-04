package com.itops.service;

import com.itops.domain.Subtask;
import com.itops.dto.CreateSubtaskRequest;
import com.itops.dto.NotificationType;
import com.itops.dto.SubtaskResponse;
import com.itops.dto.UpdateSubtaskRequest;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.SubtaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SubtaskService {

    private final SubtaskRepository subtaskRepository;
    private final NotificationService notificationService;

    public List<SubtaskResponse> getSubtasksByTask(UUID taskId, UUID companyId) {
        List<Subtask> subtasks = subtaskRepository.findByCompanyIdAndTaskIdAndDeletedAtIsNullOrderBySortOrder(companyId, taskId);
        return subtasks.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public SubtaskResponse getSubtaskById(UUID taskId, UUID subtaskId, UUID companyId) {
        Subtask subtask = subtaskRepository.findByIdAndCompanyIdAndDeletedAtIsNull(subtaskId, companyId)
                .filter(s -> s.getTaskId().equals(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));
        return toResponse(subtask);
    }

    public SubtaskResponse getSubtaskById(UUID subtaskId, UUID companyId) {
        Subtask subtask = subtaskRepository.findByIdAndCompanyIdAndDeletedAtIsNull(subtaskId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));
        return toResponse(subtask);
    }

    public SubtaskResponse createSubtask(UUID taskId, CreateSubtaskRequest request, UUID companyId, UUID userId) {
        Subtask subtask = Subtask.builder()
                .taskId(taskId)
                .title(request.getTitle())
                .description(request.getDescription())
                .status(request.getStatus() != null ? request.getStatus() : "TODO")
                .priority(request.getPriority() != null ? request.getPriority() : "MEDIUM")
                .assignedTo(request.getAssignedTo())
                .dueDate(request.getDueDate())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .storyPoints(request.getStoryPoints())
                .createdBy(userId)
                .parentSubtaskId(request.getParentSubtaskId())
                .build();
        subtask.setCompanyId(companyId);

        Subtask saved = subtaskRepository.save(subtask);
        
        // Send notification to assignee
        if (saved.getAssignedTo() != null) {
            notificationService.createNotification(
                    saved.getAssignedTo(),
                    companyId,
                    NotificationType.SUBTASK_ASSIGNED,
                    "New Subtask Assigned",
                    "You have been assigned a new subtask: " + saved.getTitle(),
                    "SUBTASK",
                    saved.getId(),
                    userId
            );
        }
        
        return toResponse(saved);
    }

    public SubtaskResponse updateSubtask(UUID taskId, UUID subtaskId, UpdateSubtaskRequest request, UUID companyId, UUID actorId) {
        Subtask subtask = subtaskRepository.findByIdAndCompanyIdAndDeletedAtIsNull(subtaskId, companyId)
                .filter(s -> s.getTaskId().equals(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        UUID oldAssignee = subtask.getAssignedTo();
        String oldStatus = subtask.getStatus();

        if (request.getTitle() != null) {
            subtask.setTitle(request.getTitle());
        }
        if (request.getStatus() != null) {
            subtask.setStatus(request.getStatus());
        }
        if (request.getSortOrder() != null) {
            subtask.setSortOrder(request.getSortOrder());
        }
        if (request.getDescription() != null) {
            subtask.setDescription(request.getDescription());
        }
        if (request.getPriority() != null) {
            subtask.setPriority(request.getPriority());
        }
        if (request.getAssignedTo() != null) {
            subtask.setAssignedTo(request.getAssignedTo());
        }
        if (request.getDueDate() != null) {
            subtask.setDueDate(request.getDueDate());
        }
        if (request.getStoryPoints() != null) {
            subtask.setStoryPoints(request.getStoryPoints());
        }

        Subtask updated = subtaskRepository.save(subtask);
        
        // Send notifications
        if (request.getAssignedTo() != null && !request.getAssignedTo().equals(oldAssignee)) {
            // Notify old assignee about unassignment
            if (oldAssignee != null) {
                notificationService.createNotification(
                        oldAssignee,
                        companyId,
                        NotificationType.SUBTASK_UNASSIGNED,
                        "Subtask Unassigned",
                        "You have been unassigned from subtask: " + updated.getTitle(),
                        "SUBTASK",
                        updated.getId(),
                        actorId
                );
            }
            
            // Notify new assignee
            notificationService.createNotification(
                    request.getAssignedTo(),
                    companyId,
                    NotificationType.SUBTASK_ASSIGNED,
                    "Subtask Assigned",
                    "You have been assigned to subtask: " + updated.getTitle(),
                    "SUBTASK",
                    updated.getId(),
                    actorId
            );
        }
        
        // Notify about status change
        if (request.getStatus() != null && !request.getStatus().equals(oldStatus)) {
            if (updated.getAssignedTo() != null) {
                notificationService.createNotification(
                        updated.getAssignedTo(),
                        companyId,
                        NotificationType.SUBTASK_STATUS_CHANGED,
                        "Subtask Status Changed",
                        "Subtask '" + updated.getTitle() + "' status changed to " + updated.getStatus(),
                        "SUBTASK",
                        updated.getId(),
                        actorId
                );
            }
            
            // Notify about completion
            if ("DONE".equals(updated.getStatus()) || "COMPLETED".equals(updated.getStatus())) {
                notificationService.createNotification(
                        updated.getCreatedBy(),
                        companyId,
                        NotificationType.SUBTASK_COMPLETED,
                        "Subtask Completed",
                        "Subtask '" + updated.getTitle() + "' has been completed",
                        "SUBTASK",
                        updated.getId(),
                        actorId
                );
            }
        }

        return toResponse(updated);
    }

    public SubtaskResponse updateSubtask(UUID subtaskId, CreateSubtaskRequest request, UUID companyId) {
        Subtask subtask = subtaskRepository.findByIdAndCompanyIdAndDeletedAtIsNull(subtaskId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        if (request.getTitle() != null) {
            subtask.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            subtask.setDescription(request.getDescription());
        }
        if (request.getStatus() != null) {
            subtask.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            subtask.setPriority(request.getPriority());
        }
        subtask.setAssignedTo(request.getAssignedTo());

        if (request.getDueDate() != null) {
            subtask.setDueDate(request.getDueDate());
        }
        if (request.getSortOrder() != null) {
            subtask.setSortOrder(request.getSortOrder());
        }
        subtask.setStoryPoints(request.getStoryPoints());

        Subtask updated = subtaskRepository.save(subtask);
        return toResponse(updated);
    }

    public SubtaskResponse patchSubtask(UUID subtaskId, CreateSubtaskRequest request, UUID companyId) {
        return updateSubtask(subtaskId, request, companyId);
    }

    public void deleteSubtask(UUID taskId, UUID subtaskId, UUID companyId) {
        Subtask subtask = subtaskRepository.findByIdAndCompanyIdAndDeletedAtIsNull(subtaskId, companyId)
                .filter(s -> s.getTaskId().equals(taskId))
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        subtask.setDeletedAt(LocalDateTime.now());
        subtaskRepository.save(subtask);
    }

    public void deleteSubtask(UUID subtaskId, UUID companyId) {
        Subtask subtask = subtaskRepository.findByIdAndCompanyIdAndDeletedAtIsNull(subtaskId, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Subtask not found"));

        subtask.setDeletedAt(LocalDateTime.now());
        subtaskRepository.save(subtask);
    }

    private SubtaskResponse toResponse(Subtask subtask) {
        return SubtaskResponse.builder()
                .id(subtask.getId())
                .companyId(subtask.getCompanyId())
                .taskId(subtask.getTaskId())
                .title(subtask.getTitle())
                .description(subtask.getDescription())
                .status(subtask.getStatus())
                .priority(subtask.getPriority())
                .assignedTo(subtask.getAssignedTo())
                .dueDate(subtask.getDueDate())
                .sortOrder(subtask.getSortOrder())
                .storyPoints(subtask.getStoryPoints())
                .createdBy(subtask.getCreatedBy())
                .parentSubtaskId(subtask.getParentSubtaskId())
                .createdAt(subtask.getCreatedAt())
                .updatedAt(subtask.getUpdatedAt())
                .build();
    }
}

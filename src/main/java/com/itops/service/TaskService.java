package com.itops.service;

import com.itops.domain.Task;
import com.itops.domain.Team;
import com.itops.domain.ProjectPhase;
import com.itops.dto.NotificationType;
import com.itops.dto.TaskRequest;
import com.itops.dto.TaskResponse;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.TaskRepository;
import com.itops.repository.TeamRepository;
import com.itops.repository.ProjectPhaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TeamRepository teamRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final ProjectMemberService projectMemberService;
    private final NotificationService notificationService;

    public List<TaskResponse> getAllTasks(UUID companyId) {
        return taskRepository.findByCompanyId(companyId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getTasksByProjectAndPhase(UUID projectId, UUID phaseId) {
        List<Task> tasks;
        if (phaseId != null) {
            tasks = taskRepository.findByProjectIdAndPhaseId(projectId, phaseId);
        } else {
            tasks = taskRepository.findByProjectId(projectId);
        }
        return tasks.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<TaskResponse> getTasksWithFilters(UUID companyId, UUID userId, String userRole, 
                                                   UUID projectId, UUID phaseId, UUID teamId, UUID assignedTo) {
        log.info("getTasksWithFilters - userId: {}, role: {}, companyId: {}, projectId: {}, phaseId: {}, teamId: {}, assignedTo: {}", 
            userId, userRole, companyId, projectId, phaseId, teamId, assignedTo);
            
        List<Task> tasks;
        
        // Start with base query - project or company level
        if (projectId != null && phaseId != null) {
            tasks = taskRepository.findByProjectIdAndPhaseId(projectId, phaseId);
            log.info("Found {} tasks by projectId and phaseId", tasks.size());
        } else if (projectId != null) {
            tasks = taskRepository.findByProjectId(projectId);
            log.info("Found {} tasks by projectId", tasks.size());
        } else {
            tasks = taskRepository.findByCompanyId(companyId);
            log.info("Found {} tasks by companyId", tasks.size());
        }
        
        // Apply role-based filtering for SUPER_USER
        if ("SUPER_USER".equals(userRole) && teamId == null) {
            // SUPER_USER without team filter should only see tasks from their own teams
            List<Team> userTeams = teamRepository.findByCreatedByUserId(userId);
            List<UUID> userTeamIds = userTeams.stream()
                    .map(Team::getId)
                    .collect(Collectors.toList());
            
            log.info("SUPER_USER has {} teams: {}", userTeamIds.size(), userTeamIds);
            
            int beforeFilter = tasks.size();
            tasks = tasks.stream()
                    .filter(task -> userTeamIds.contains(task.getTeamId()))
                    .collect(Collectors.toList());
            log.info("After SUPER_USER team filter: {} tasks (was {})", tasks.size(), beforeFilter);
        }
        
        // Apply teamId filter if provided (explicit team selection)
        if (teamId != null) {
            final UUID finalTeamId = teamId;
            int beforeFilter = tasks.size();
            tasks = tasks.stream()
                    .filter(task -> finalTeamId.equals(task.getTeamId()))
                    .collect(Collectors.toList());
            log.info("After teamId filter: {} tasks (was {})", tasks.size(), beforeFilter);
        }
        
        // Apply assignedTo filter if provided
        if (assignedTo != null) {
            final UUID finalAssignedTo = assignedTo;
            int beforeFilter = tasks.size();
            tasks = tasks.stream()
                    .filter(task -> finalAssignedTo.equals(task.getAssignedTo()))
                    .collect(Collectors.toList());
            log.info("After assignedTo filter: {} tasks (was {})", tasks.size(), beforeFilter);
        }
        
        return tasks.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TaskResponse getTaskById(UUID id, UUID companyId) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        return toResponse(task);
    }

    public TaskResponse createTask(TaskRequest request, UUID companyId, UUID userId) {
        // Resolve team: use explicit teamId or get from phase
        UUID resolvedTeamId = resolveTeamId(request.getTeamId(), request.getPhaseId());
        
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .projectId(request.getProjectId())
                .phaseId(request.getPhaseId())
                .teamId(resolvedTeamId)
                .assignedTo(request.getAssignedTo())
                .status(request.getStatus())
                .priority(request.getPriority())
                .dueDate(request.getDueDate() != null ? LocalDate.parse(request.getDueDate()) : null)
                .estimatedHours(request.getEstimatedHours())
                .storyPoints(request.getStoryPoints())
                .createdBy(userId)
                .build();
        task.setCompanyId(companyId);

        Task saved = taskRepository.save(task);
        
        // Auto-add member to project if assigned
        if (saved.getAssignedTo() != null && saved.getProjectId() != null) {
            projectMemberService.autoAddMemberIfNeeded(saved.getProjectId(), saved.getAssignedTo(), companyId);
        }
        
        // Notify assignee about new task
        if (saved.getAssignedTo() != null) {
            log.info("Creating notification for task assignment - TaskId: {}, AssignedTo: {}, ActorId: {}", 
                saved.getId(), saved.getAssignedTo(), userId);
            notificationService.createNotification(
                saved.getAssignedTo(),
                companyId,
                NotificationType.TASK_ASSIGNED,
                "New Task Assigned",
                String.format("You have been assigned to task: %s", saved.getTitle()),
                "TASK",
                saved.getId(),
                userId
            );
        } else {
            log.info("No assignee for task: {}", saved.getId());
        }
        
        return toResponse(saved);
    }

    public TaskResponse updateTask(UUID id, TaskRequest request, UUID companyId, UUID actorId) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Track changes for notifications
        String oldStatus = task.getStatus();
        UUID oldAssignee = task.getAssignedTo();
        
        // Resolve team: use explicit teamId or get from phase
        UUID resolvedTeamId = resolveTeamId(request.getTeamId(), request.getPhaseId());

        task.setTitle(request.getTitle());
        task.setDescription(request.getDescription());
        task.setProjectId(request.getProjectId());
        task.setPhaseId(request.getPhaseId());
        task.setTeamId(resolvedTeamId);
        task.setAssignedTo(request.getAssignedTo());
        task.setStatus(request.getStatus());
        task.setPriority(request.getPriority());
        task.setDueDate(request.getDueDate() != null ? LocalDate.parse(request.getDueDate()) : null);
        task.setEstimatedHours(request.getEstimatedHours());
        task.setStoryPoints(request.getStoryPoints());

        Task updated = taskRepository.save(task);
        
        // Auto-add member to project if assigned
        if (updated.getAssignedTo() != null && updated.getProjectId() != null) {
            projectMemberService.autoAddMemberIfNeeded(updated.getProjectId(), updated.getAssignedTo(), companyId);
        }
        
        // Notify on assignee change
        if (oldAssignee != null && updated.getAssignedTo() != null && !oldAssignee.equals(updated.getAssignedTo())) {
            log.info("Task reassignment detected - OldAssignee: {}, NewAssignee: {}, ActorId: {}", 
                oldAssignee, updated.getAssignedTo(), actorId);
            // Notify old assignee
            notificationService.createNotification(
                oldAssignee,
                companyId,
                NotificationType.TASK_UNASSIGNED,
                "Task Unassigned",
                String.format("You have been unassigned from task: %s", updated.getTitle()),
                "TASK",
                updated.getId(),
                actorId
            );
            // Notify new assignee
            notificationService.createNotification(
                updated.getAssignedTo(),
                companyId,
                NotificationType.TASK_ASSIGNED,
                "Task Assigned",
                String.format("You have been assigned to task: %s", updated.getTitle()),
                "TASK",
                updated.getId(),
                actorId
            );
        } else if (oldAssignee == null && updated.getAssignedTo() != null) {
            // New assignment
            log.info("Initial task assignment detected - NewAssignee: {}, TaskId: {}, ActorId: {}", 
                updated.getAssignedTo(), updated.getId(), actorId);
            notificationService.createNotification(
                updated.getAssignedTo(),
                companyId,
                NotificationType.TASK_ASSIGNED,
                "Task Assigned",
                String.format("You have been assigned to task: %s", updated.getTitle()),
                "TASK",
                updated.getId(),
                actorId
            );
        } else if (oldAssignee != null && updated.getAssignedTo() == null) {
            // Unassignment
            log.info("Task unassignment detected - OldAssignee: {}, TaskId: {}, ActorId: {}", 
                oldAssignee, updated.getId(), actorId);
            notificationService.createNotification(
                oldAssignee,
                companyId,
                NotificationType.TASK_UNASSIGNED,
                "Task Unassigned",
                String.format("You have been unassigned from task: %s", updated.getTitle()),
                "TASK",
                updated.getId(),
                actorId
            );
        }
        
        // Notify on status change
        if (oldStatus != null && !oldStatus.equals(updated.getStatus()) && updated.getAssignedTo() != null) {
            log.info("Task status change detected - TaskId: {}, OldStatus: {}, NewStatus: {}, AssignedTo: {}, ActorId: {}", 
                updated.getId(), oldStatus, updated.getStatus(), updated.getAssignedTo(), actorId);
            notificationService.createNotification(
                updated.getAssignedTo(),
                companyId,
                NotificationType.TASK_STATUS_CHANGED,
                "Task Status Changed",
                String.format("Task '%s' status changed from %s to %s", updated.getTitle(), oldStatus, updated.getStatus()),
                "TASK",
                updated.getId(),
                actorId
            );
        }
        
        return toResponse(updated);
    }

    public TaskResponse patchTask(UUID id, TaskRequest request, UUID companyId, UUID actorId) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));

        // Track old values for notifications
        UUID oldAssignee = task.getAssignedTo();

        // Only update fields that are provided
        if (request.getTitle() != null) {
            task.setTitle(request.getTitle());
        }
        if (request.getDescription() != null) {
            task.setDescription(request.getDescription());
        }
        if (request.getProjectId() != null) {
            task.setProjectId(request.getProjectId());
        }
        if (request.getPhaseId() != null) {
            task.setPhaseId(request.getPhaseId());
            // If phase changes, update team from the new phase (unless teamId is explicitly set)
            if (request.getTeamId() == null) {
                UUID phaseTeamId = resolveTeamId(null, request.getPhaseId());
                if (phaseTeamId != null) {
                    task.setTeamId(phaseTeamId);
                }
            }
        }
        if (request.getTeamId() != null) {
            task.setTeamId(request.getTeamId());
        }
        // Only update assignedTo if explicitly provided (not null)
        // This prevents accidentally clearing the assignee when updating other fields
        if (request.getAssignedTo() != null) {
            task.setAssignedTo(request.getAssignedTo());
        }
        if (request.getStatus() != null) {
            task.setStatus(request.getStatus());
        }
        if (request.getPriority() != null) {
            task.setPriority(request.getPriority());
        }
        if (request.getDueDate() != null) {
            task.setDueDate(LocalDate.parse(request.getDueDate()));
        }
        if (request.getEstimatedHours() != null) {
            task.setEstimatedHours(request.getEstimatedHours());
        }
        if (request.getStoryPoints() != null) {
            task.setStoryPoints(request.getStoryPoints());
        }

        Task updated = taskRepository.save(task);
        
        // Auto-add member to project if assigned
        if (updated.getAssignedTo() != null && updated.getProjectId() != null) {
            projectMemberService.autoAddMemberIfNeeded(updated.getProjectId(), updated.getAssignedTo(), companyId);
        }
        
        // Notify on assignee change (from patchTask)
        if (request.getAssignedTo() != null) {
            // Reassignment case (changing from one user to another)
            if (oldAssignee != null && updated.getAssignedTo() != null && !oldAssignee.equals(updated.getAssignedTo())) {
                log.info("Task reassignment detected (patchTask) - OldAssignee: {}, NewAssignee: {}, ActorId: {}", 
                    oldAssignee, updated.getAssignedTo(), actorId);
                // Notify old assignee
                notificationService.createNotification(
                    oldAssignee,
                    companyId,
                    NotificationType.TASK_UNASSIGNED,
                    "Task Unassigned",
                    String.format("You have been unassigned from task: %s", updated.getTitle()),
                    "TASK",
                    updated.getId(),
                    actorId
                );
                // Notify new assignee
                notificationService.createNotification(
                    updated.getAssignedTo(),
                    companyId,
                    NotificationType.TASK_ASSIGNED,
                    "Task Assigned",
                    String.format("You have been assigned to task: %s", updated.getTitle()),
                    "TASK",
                    updated.getId(),
                    actorId
                );
            } else if (oldAssignee == null && updated.getAssignedTo() != null) {
                // Initial assignment
                log.info("Initial task assignment detected (patchTask) - NewAssignee: {}, TaskId: {}, ActorId: {}", 
                    updated.getAssignedTo(), updated.getId(), actorId);
                notificationService.createNotification(
                    updated.getAssignedTo(),
                    companyId,
                    NotificationType.TASK_ASSIGNED,
                    "Task Assigned",
                    String.format("You have been assigned to task: %s", updated.getTitle()),
                    "TASK",
                    updated.getId(),
                    actorId
                );
            } else if (oldAssignee != null && updated.getAssignedTo() == null) {
                // Unassignment
                log.info("Task unassignment detected (patchTask) - OldAssignee: {}, TaskId: {}, ActorId: {}", 
                    oldAssignee, updated.getId(), actorId);
                notificationService.createNotification(
                    oldAssignee,
                    companyId,
                    NotificationType.TASK_UNASSIGNED,
                    "Task Unassigned",
                    String.format("You have been unassigned from task: %s", updated.getTitle()),
                    "TASK",
                    updated.getId(),
                    actorId
                );
            }
        }
        
        return toResponse(updated);
    }

    public void deleteTask(UUID id, UUID companyId) {
        Task task = taskRepository.findById(id)
                .filter(t -> t.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Task not found"));
        taskRepository.delete(task);
    }

    /**
     * Resolves the team ID for a task.
     * If teamId is explicitly provided, use it.
     * Otherwise, if phaseId is provided, get team from the phase.
     */
    private UUID resolveTeamId(UUID teamId, UUID phaseId) {
        if (teamId != null) {
            return teamId;
        }
        if (phaseId != null) {
            return projectPhaseRepository.findById(phaseId)
                    .map(ProjectPhase::getTeamId)
                    .orElse(null);
        }
        return null;
    }

    private TaskResponse toResponse(Task task) {
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .projectId(task.getProjectId())
                .phaseId(task.getPhaseId())
                .teamId(task.getTeamId())
                .assignedTo(task.getAssignedTo())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate() != null ? task.getDueDate().toString() : null)
                .estimatedHours(task.getEstimatedHours())
                .storyPoints(task.getStoryPoints())
                .createdBy(task.getCreatedBy())
                .createdAt(task.getCreatedAt())
                .build();
    }
}

package com.itops.service;

import com.itops.domain.Project;
import com.itops.domain.ProjectMember;
import com.itops.domain.ProjectPhase;
import com.itops.domain.Task;
import com.itops.domain.TimeEntry;
import com.itops.domain.User;
import com.itops.dto.NotificationType;
import com.itops.dto.ProjectRequest;
import com.itops.dto.ProjectResponse;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.ProjectMemberRepository;
import com.itops.repository.ProjectPhaseRepository;
import com.itops.repository.ProjectRepository;
import com.itops.repository.TaskRepository;
import com.itops.repository.TimeEntryRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final TaskRepository taskRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final NotificationService notificationService;
    private final SubscriptionGuard subscriptionGuard;

    public List<ProjectResponse> getAllProjects(UUID companyId) {
        return projectRepository.findByCompanyId(companyId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public List<ProjectResponse> getAllProjectsWithFilters(
            UUID companyId,
            String status,
            String priority,
            String clientId,
            String search,
            String sortBy,
            String userRole,
            UUID teamId,
            UUID userId) {
        
        log.info("Getting projects for companyId: {}, userRole: {}, userId: {}, teamId: {}", 
            companyId, userRole, userId, teamId);
        
        // Get all projects for the company
        Stream<Project> projectStream = projectRepository.findByCompanyId(companyId).stream();
        List<Project> allProjects = projectStream.collect(Collectors.toList());
        log.info("Total projects in company: {}", allProjects.size());
        projectStream = allProjects.stream();
        
        // For ADMIN and USER roles: Filter to only show projects owned by their team members or manager
        if (("ADMIN".equals(userRole) || "USER".equals(userRole)) && userId != null) {
            // Get all users in the same team (if teamId exists)
            Set<UUID> allowedOwnerIds = new HashSet<>();
            Set<UUID> allowedProjectIds = new HashSet<>();
            
            if (teamId != null) {
                List<UUID> teamMemberIds = userRepository.findByTeamId(teamId)
                        .stream()
                        .map(User::getId)
                        .collect(Collectors.toList());
                allowedOwnerIds.addAll(teamMemberIds);
                
                // Add projects with phases assigned to user's team
                List<UUID> projectsWithTeamPhases = projectPhaseRepository.findProjectIdsByTeamId(teamId);
                allowedProjectIds.addAll(projectsWithTeamPhases);
            }
            
            // Get current user to find their manager (createdByUserId)
            userRepository.findById(userId).ifPresent(currentUser -> {
                if (currentUser.getCreatedByUserId() != null) {
                    allowedOwnerIds.add(currentUser.getCreatedByUserId());
                }
            });
            
            // Also allow projects owned by the user themselves
            allowedOwnerIds.add(userId);
            
            // Filter projects where:
            // 1. Project has phases assigned to the team, OR
            // 2. ProjectOwnerId is null (unassigned - visible to team), OR
            // 3. ProjectOwnerId is in the allowed list (owned by team member or their manager)
            final Set<UUID> finalAllowedOwnerIds = allowedOwnerIds;
            final Set<UUID> finalAllowedProjectIds = allowedProjectIds;
            projectStream = projectStream.filter(p -> 
                finalAllowedProjectIds.contains(p.getId()) ||
                p.getProjectOwnerId() == null ||
                finalAllowedOwnerIds.contains(p.getProjectOwnerId())
            );
            
            List<Project> filteredProjects = projectStream.collect(Collectors.toList());
            log.info("After filtering for ADMIN/USER - Projects visible: {}", filteredProjects.size());
            projectStream = filteredProjects.stream();
        } else {
            log.info("No filtering applied for role: {}", userRole);
        }
        
        // Apply status filter
        if (status != null && !status.isEmpty()) {
            projectStream = projectStream.filter(p -> status.equalsIgnoreCase(p.getStatus()));
        }
        
        // Apply priority filter
        if (priority != null && !priority.isEmpty()) {
            projectStream = projectStream.filter(p -> priority.equalsIgnoreCase(p.getPriority()));
        }
        
        // Apply client filter
        if (clientId != null && !clientId.isEmpty()) {
            try {
                UUID clientUUID = UUID.fromString(clientId);
                projectStream = projectStream.filter(p -> 
                    p.getClientId() != null && p.getClientId().equals(clientUUID)
                );
            } catch (IllegalArgumentException e) {
                // Invalid UUID, skip filter
            }
        }
        
        // Apply search filter (name or description)
        if (search != null && !search.isEmpty()) {
            String searchLower = search.toLowerCase();
            projectStream = projectStream.filter(p ->
                p.getName().toLowerCase().contains(searchLower) ||
                (p.getDescription() != null && p.getDescription().toLowerCase().contains(searchLower))
            );
        }
        
        // Apply sorting
        Comparator<Project> comparator;
        switch (sortBy != null ? sortBy : "updatedAt") {
            case "name":
                comparator = Comparator.comparing(Project::getName);
                break;
            case "deadline":
                comparator = Comparator.comparing(Project::getEndDate, 
                    Comparator.nullsLast(Comparator.naturalOrder()));
                break;
            default: // updatedAt
                comparator = Comparator.comparing(Project::getUpdatedAt).reversed();
                break;
        }
        
        return projectStream
                .sorted(comparator)
                .map(this::toResponseWithOverview)
                .collect(Collectors.toList());
    }

    public ProjectResponse getProjectById(UUID id, UUID companyId) {
        Project project = projectRepository.findById(id)
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        return toResponseWithOverview(project);
    }

    public ProjectResponse createProject(ProjectRequest request, UUID companyId, UUID actorId) {
        // Enforce subscription limits
        subscriptionGuard.enforceProjectCreation(companyId);
        
        Project project = Project.builder()
                .name(request.getName())
                .description(request.getDescription())
                .clientId(request.getClientId())
                .status(request.getStatus())
                .priority(request.getPriority())
                .startDate(request.getStartDate() != null ? LocalDate.parse(request.getStartDate()) : null)
                .endDate(request.getEndDate() != null ? LocalDate.parse(request.getEndDate()) : null)
                .projectOwnerId(request.getProjectOwnerId())
                .build();
        project.setCompanyId(companyId);

        Project saved = projectRepository.save(project);
        
        // Notify project owner and all project members
        notifyProjectCreated(saved, actorId);
        
        return toResponse(saved);
    }

    public ProjectResponse updateProject(UUID id, ProjectRequest request, UUID companyId, UUID actorId) {
        Project project = projectRepository.findById(id)
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        
        log.info("Updating project {} with projectOwnerId: {}", id, request.getProjectOwnerId());
        
        // Track changes for notifications
        String oldStatus = project.getStatus();
        UUID oldOwnerId = project.getProjectOwnerId();
        
        project.setName(request.getName());
        project.setDescription(request.getDescription());
        project.setClientId(request.getClientId());
        project.setStatus(request.getStatus());
        project.setPriority(request.getPriority());
        project.setStartDate(request.getStartDate() != null ? LocalDate.parse(request.getStartDate()) : null);
        project.setEndDate(request.getEndDate() != null ? LocalDate.parse(request.getEndDate()) : null);
        project.setProjectOwnerId(request.getProjectOwnerId());
        
        log.info("Project projectOwnerId after setting: {}", project.getProjectOwnerId());
        
        Project updated = projectRepository.save(project);
        
        log.info("Project projectOwnerId after save: {}", updated.getProjectOwnerId());
        
        // Notify on status change
        if (oldStatus != null && !oldStatus.equals(updated.getStatus())) {
            notifyProjectStatusChanged(updated, oldStatus, actorId);
        }
        
        // Notify on owner change
        if (oldOwnerId != null && !oldOwnerId.equals(updated.getProjectOwnerId())) {
            notifyProjectOwnerChanged(updated, oldOwnerId, actorId);
        }
        
        return toResponse(updated);
    }

    public void deleteProject(UUID id, UUID companyId) {
        Project project = projectRepository.findById(id)
                .filter(p -> p.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Project not found"));
        projectRepository.delete(project);
    }

    private ProjectResponse toResponse(Project project) {
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .clientId(project.getClientId())
                .status(project.getStatus())
                .priority(project.getPriority())
                .startDate(project.getStartDate() != null ? project.getStartDate().toString() : null)
                .endDate(project.getEndDate() != null ? project.getEndDate().toString() : null)
                .projectOwnerId(project.getProjectOwnerId())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
    
    private ProjectResponse toResponseWithOverview(Project project) {
        // Get all tasks for this project
        List<Task> tasks = taskRepository.findByProjectId(project.getId());
        int taskCount = tasks.size();
        int completedTaskCount = (int) tasks.stream()
                .filter(t -> "DONE".equals(t.getStatus()))
                .count();
        
        // Calculate progress percentage
        int progressPercent = taskCount > 0 ? (completedTaskCount * 100) / taskCount : 0;
        
        // Get all time entries for this project
        List<TimeEntry> timeEntries = timeEntryRepository.findByProjectId(project.getId());
        
        // Calculate total hours and billable hours
        // Time entries store hours directly in the 'hours' field
        // But if startTime/endTime are set, calculate from those instead
        double totalHours = timeEntries.stream()
                .mapToDouble(te -> {
                    if (te.getStartTime() != null && te.getEndTime() != null) {
                        return calculateDurationInMinutes(te) / 60.0;
                    }
                    return te.getHours() != null ? te.getHours().doubleValue() : 0.0;
                })
                .sum();
        
        double billableHours = timeEntries.stream()
                .filter(te -> Boolean.TRUE.equals(te.getIsBillable()))
                .mapToDouble(te -> {
                    if (te.getStartTime() != null && te.getEndTime() != null) {
                        return calculateDurationInMinutes(te) / 60.0;
                    }
                    return te.getHours() != null ? te.getHours().doubleValue() : 0.0;
                })
                .sum();
        
        // Round to 1 decimal place
        totalHours = Math.round(totalHours * 10.0) / 10.0;
        billableHours = Math.round(billableHours * 10.0) / 10.0;
        
        // Get team member count
        int teamMemberCount = projectMemberRepository.findByProjectIdAndCompanyId(
                project.getId(), project.getCompanyId()).size();
        
        // Get phase counts
        List<ProjectPhase> phases = projectPhaseRepository.findByCompanyIdAndProjectIdAndDeletedAtIsNullOrderBySortOrder(
                project.getCompanyId(), project.getId());
        int phaseCount = phases.size();
        int activePhaseCount = (int) phases.stream()
                .filter(p -> "ACTIVE".equals(p.getStatus()))
                .count();
        
        return ProjectResponse.builder()
                .id(project.getId())
                .name(project.getName())
                .description(project.getDescription())
                .clientId(project.getClientId())
                .status(project.getStatus())
                .priority(project.getPriority())
                .startDate(project.getStartDate() != null ? project.getStartDate().toString() : null)
                .endDate(project.getEndDate() != null ? project.getEndDate().toString() : null)
                .projectOwnerId(project.getProjectOwnerId())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .taskCount(taskCount)
                .completedTaskCount(completedTaskCount)
                .progressPercent(progressPercent)
                .totalHours(totalHours)
                .billableHours(billableHours)
                .teamMemberCount(teamMemberCount)
                .phaseCount(phaseCount)
                .activePhaseCount(activePhaseCount)
                .build();
    }
    
    private double calculateDurationInMinutes(TimeEntry entry) {
        if (entry.getStartTime() != null && entry.getEndTime() != null) {
            return Duration.between(entry.getStartTime(), entry.getEndTime()).toMinutes();
        }
        return 0.0;
    }
    
    // Notification helper methods
    private void notifyProjectCreated(Project project, UUID actorId) {
        List<UUID> recipientIds = getProjectRecipients(project);
        String title = "New Project Created";
        String message = String.format("Project '%s' has been created", project.getName());
        
        notificationService.createNotifications(
                recipientIds,
                project.getCompanyId(),
                NotificationType.PROJECT_CREATED,
                title,
                message,
                "PROJECT",
                project.getId(),
                actorId
        );
    }
    
    private void notifyProjectStatusChanged(Project project, String oldStatus, UUID actorId) {
        List<UUID> recipientIds = getProjectRecipients(project);
        String title = "Project Status Changed";
        String message = String.format("Project '%s' status changed from %s to %s", 
                project.getName(), oldStatus, project.getStatus());
        
        notificationService.createNotifications(
                recipientIds,
                project.getCompanyId(),
                NotificationType.PROJECT_STATUS_CHANGED,
                title,
                message,
                "PROJECT",
                project.getId(),
                actorId
        );
    }
    
    private void notifyProjectOwnerChanged(Project project, UUID oldOwnerId, UUID actorId) {
        // Notify old owner about removal
        if (oldOwnerId != null) {
            notificationService.createNotification(
                    oldOwnerId,
                    project.getCompanyId(),
                    NotificationType.PROJECT_UPDATED,
                    "Project Owner Changed",
                    String.format("You have been removed as owner of project '%s'", project.getName()),
                    "PROJECT",
                    project.getId(),
                    actorId
            );
        }
        
        // Notify new owner about assignment
        if (project.getProjectOwnerId() != null) {
            notificationService.createNotification(
                    project.getProjectOwnerId(),
                    project.getCompanyId(),
                    NotificationType.PROJECT_UPDATED,
                    "Project Owner Changed",
                    String.format("You have been assigned as owner of project '%s'", project.getName()),
                    "PROJECT",
                    project.getId(),
                    actorId
            );
        }
    }
    
    private List<UUID> getProjectRecipients(Project project) {
        Set<UUID> recipients = new HashSet<>();
        
        // Add project owner
        if (project.getProjectOwnerId() != null) {
            recipients.add(project.getProjectOwnerId());
        }
        
        // Add all project members
        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndCompanyId(
                project.getId(), project.getCompanyId());
        members.forEach(member -> {
            if (member.getUser() != null) {
                recipients.add(member.getUser().getId());
            }
        });
        
        return recipients.stream().toList();
    }
}

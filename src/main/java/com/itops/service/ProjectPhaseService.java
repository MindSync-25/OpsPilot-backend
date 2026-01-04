package com.itops.service;

import com.itops.domain.ProjectPhase;
import com.itops.domain.Team;
import com.itops.domain.Task;
import com.itops.domain.User;
import com.itops.dto.CreatePhaseRequest;
import com.itops.dto.NotificationType;
import com.itops.dto.PhaseResponse;
import com.itops.dto.UpdatePhaseRequest;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.ProjectPhaseRepository;
import com.itops.repository.TeamRepository;
import com.itops.repository.TaskRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProjectPhaseService {

    private final ProjectPhaseRepository phaseRepository;
    private final TeamRepository teamRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;
    private final UserRepository userRepository;

    public List<PhaseResponse> getPhasesByProject(UUID projectId, UUID companyId, String userRole, UUID teamId, UUID userId) {
        List<ProjectPhase> phases = phaseRepository.findByCompanyIdAndProjectIdAndDeletedAtIsNullOrderBySortOrder(companyId, projectId);
        
        // Filter phases based on user role
        if ("SUPER_USER".equals(userRole)) {
            // SUPER_USER can only see phases assigned to their teams
            List<Team> userTeams = teamRepository.findByCreatedByUserId(userId);
            List<UUID> userTeamIds = userTeams.stream()
                    .map(Team::getId)
                    .collect(Collectors.toList());
            
            phases = phases.stream()
                    .filter(phase -> userTeamIds.contains(phase.getTeamId()))
                    .collect(Collectors.toList());
        } else if ("USER".equals(userRole) && teamId != null) {
            // Regular USER can only see phases assigned to their team
            phases = phases.stream()
                    .filter(phase -> teamId.equals(phase.getTeamId()))
                    .collect(Collectors.toList());
        }
        // ADMIN and TOP_USER can see all phases
        
        return phases.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public PhaseResponse getPhaseById(UUID projectId, UUID phaseId, UUID companyId) {
        ProjectPhase phase = phaseRepository.findByIdAndCompanyIdAndDeletedAtIsNull(phaseId, companyId)
                .filter(p -> p.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Phase not found"));
        return toResponse(phase);
    }

    public PhaseResponse createPhase(UUID projectId, CreatePhaseRequest request, UUID companyId) {
        ProjectPhase phase = ProjectPhase.builder()
                .projectId(projectId)
                .name(request.getName())
                .description(request.getDescription())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .status(request.getStatus() != null ? request.getStatus() : "TODO")
                .teamId(request.getTeamId())
                .build();
        phase.setCompanyId(companyId);

        ProjectPhase saved = phaseRepository.save(phase);
        
        // Notify team members if team is assigned
        if (request.getTeamId() != null) {
            Team team = teamRepository.findById(request.getTeamId()).orElse(null);
            if (team != null) {
                List<User> teamMembers = userRepository.findByTeamId(team.getId());
                for (User member : teamMembers) {
                    notificationService.createNotification(
                            member.getId(),
                            companyId,
                            NotificationType.PHASE_CREATED,
                            "New Phase Assigned to Your Team",
                            "Phase '" + saved.getName() + "' has been assigned to your team",
                            "PHASE",
                            saved.getId(),
                            null
                    );
                }
            }
        }
        
        return toResponse(saved);
    }

    public PhaseResponse updatePhase(UUID projectId, UUID phaseId, UpdatePhaseRequest request, UUID companyId) {
        ProjectPhase phase = phaseRepository.findByIdAndCompanyIdAndDeletedAtIsNull(phaseId, companyId)
                .filter(p -> p.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Phase not found"));

        String oldStatus = phase.getStatus();
        UUID oldTeamId = phase.getTeamId();
        
        if (request.getName() != null) {
            phase.setName(request.getName());
        }
        if (request.getDescription() != null) {
            phase.setDescription(request.getDescription());
        }
        if (request.getSortOrder() != null) {
            phase.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            phase.setStatus(request.getStatus());
        }
        if (request.getTeamId() != null) {
            phase.setTeamId(request.getTeamId());
        }

        ProjectPhase updated = phaseRepository.save(phase);
        
        // Notify on status change
        if (request.getStatus() != null && !request.getStatus().equals(oldStatus)) {
            if (updated.getTeamId() != null) {
                List<User> teamMembers = userRepository.findByTeamId(updated.getTeamId());
                for (User member : teamMembers) {
                    notificationService.createNotification(
                            member.getId(),
                            companyId,
                            NotificationType.PHASE_STATUS_CHANGED,
                            "Phase Status Changed",
                            "Phase '" + updated.getName() + "' status changed to: " + updated.getStatus(),
                            "PHASE",
                            updated.getId(),
                            null
                    );
                }
            }
        }
        
        // Notify on team change
        if (request.getTeamId() != null && !request.getTeamId().equals(oldTeamId)) {
            // Notify new team members
            List<User> newTeamMembers = userRepository.findByTeamId(request.getTeamId());
            for (User member : newTeamMembers) {
                notificationService.createNotification(
                        member.getId(),
                        companyId,
                        NotificationType.PHASE_CREATED,
                        "Phase Assigned to Your Team",
                        "Phase '" + updated.getName() + "' has been assigned to your team",
                        "PHASE",
                        updated.getId(),
                        null
                );
            }
        }
        
        return toResponse(updated);
    }

    public void deletePhase(UUID projectId, UUID phaseId, UUID companyId) {
        ProjectPhase phase = phaseRepository.findByIdAndCompanyIdAndDeletedAtIsNull(phaseId, companyId)
                .filter(p -> p.getProjectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Phase not found"));

        // Soft delete
        phase.setDeletedAt(LocalDateTime.now());
        phaseRepository.save(phase);
    }

    private PhaseResponse toResponse(ProjectPhase phase) {
        // Count tasks in this phase
        long taskCount = taskRepository.findByPhaseId(phase.getId()).stream()
                .filter(t -> t.getDeletedAt() == null)
                .count();

        // Get team name if teamId exists
        String teamName = null;
        if (phase.getTeamId() != null) {
            teamName = teamRepository.findById(phase.getTeamId())
                    .map(Team::getName)
                    .orElse(null);
        }

        return PhaseResponse.builder()
                .id(phase.getId())
                .companyId(phase.getCompanyId())
                .projectId(phase.getProjectId())
                .name(phase.getName())
                .description(phase.getDescription())
                .sortOrder(phase.getSortOrder())
                .status(phase.getStatus())
                .teamId(phase.getTeamId())
                .teamName(teamName)
                .createdAt(phase.getCreatedAt())
                .updatedAt(phase.getUpdatedAt())
                .taskCount(taskCount)
                .build();
    }
}

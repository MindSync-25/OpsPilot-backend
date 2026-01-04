package com.itops.service;

import com.itops.dto.NotificationType;
import com.itops.dto.request.AddProjectMemberRequest;
import com.itops.dto.response.ProjectMemberResponse;
import com.itops.domain.Project;
import com.itops.domain.ProjectMember;
import com.itops.domain.Task;
import com.itops.domain.User;
import com.itops.repository.ProjectMemberRepository;
import com.itops.repository.ProjectRepository;
import com.itops.repository.TaskRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectMemberService {

    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<ProjectMemberResponse> getProjectMembers(UUID projectId, UUID companyId) {
        log.info("Getting members for project: {} in company: {}", projectId, companyId);
        
        // Verify project exists and belongs to company
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Project does not belong to your company");
        }
        
        List<ProjectMember> members = projectMemberRepository.findByProjectIdAndCompanyId(projectId, companyId);
        
        return members.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    @Transactional
    public ProjectMemberResponse addMember(UUID projectId, AddProjectMemberRequest request, UUID companyId) {
        log.info("Adding member {} to project: {}", request.getUserId(), projectId);
        
        // Verify project exists and belongs to company
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Project does not belong to your company");
        }
        
        // Verify user exists and belongs to company
        User user = userRepository.findById(request.getUserId())
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getCompanyId().equals(companyId)) {
            throw new RuntimeException("User does not belong to your company");
        }
        
        // Check if user is already a member
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, request.getUserId())) {
            throw new RuntimeException("User is already a member of this project");
        }
        
        // Create project member
        ProjectMember member = ProjectMember.builder()
            .companyId(companyId)
            .project(project)
            .user(user)
            .roleInProject(request.getRoleInProject())
            .build();
        
        ProjectMember savedMember = projectMemberRepository.save(member);
        log.info("Successfully added member to project");
        
        // Notify the new member
        notificationService.createNotification(
                request.getUserId(),
                companyId,
                NotificationType.PROJECT_MEMBER_ADDED,
                "Added to Project",
                "You have been added to project: " + project.getName(),
                "PROJECT",
                projectId,
                null
        );
        
        return toResponse(savedMember);
    }

    @Transactional
    public void removeMember(UUID projectId, UUID userId, UUID companyId) {
        log.info("Removing member {} from project: {}", userId, projectId);
        
        // Verify project exists and belongs to company
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Project does not belong to your company");
        }
        
        // Find the member
        ProjectMember member = projectMemberRepository.findByProjectIdAndUserIdAndCompanyId(projectId, userId, companyId)
            .orElseThrow(() -> new RuntimeException("Member not found in this project"));
        
        // Soft delete by calling repository delete (triggers @SQLDelete)
        projectMemberRepository.delete(member);
        log.info("Successfully removed member from project");
        
        // Notify the removed member
        notificationService.createNotification(
                userId,
                companyId,
                NotificationType.PROJECT_MEMBER_REMOVED,
                "Removed from Project",
                "You have been removed from project: " + project.getName(),
                "PROJECT",
                projectId,
                null
        );
    }
    
    @Transactional
    public void autoAddMemberIfNeeded(UUID projectId, UUID userId, UUID companyId) {
        // Check if user is already a member
        if (projectMemberRepository.existsByProjectIdAndUserId(projectId, userId)) {
            return; // Already a member, nothing to do
        }
        
        log.info("Auto-adding member {} to project: {}", userId, projectId);
        
        // Verify project exists and belongs to company
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Project does not belong to your company");
        }
        
        // Verify user exists and belongs to company
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
        
        if (!user.getCompanyId().equals(companyId)) {
            return; // User doesn't belong to this company, skip
        }
        
        // Create project member
        ProjectMember member = ProjectMember.builder()
            .companyId(companyId)
            .project(project)
            .user(user)
            .build();
        
        projectMemberRepository.save(member);
        log.info("Successfully auto-added member to project");
    }
    
    @Transactional
    public int syncProjectMembersFromTasks(UUID projectId, UUID companyId) {
        log.info("Syncing project members from tasks for project: {}", projectId);
        
        // Verify project exists and belongs to company
        Project project = projectRepository.findById(projectId)
            .orElseThrow(() -> new RuntimeException("Project not found"));
        
        if (!project.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Project does not belong to your company");
        }
        
        // Get all tasks for this project that have assigned users
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        
        int addedCount = 0;
        for (Task task : tasks) {
            if (task.getAssignedTo() != null) {
                // Check if user is already a member
                if (!projectMemberRepository.existsByProjectIdAndUserId(projectId, task.getAssignedTo())) {
                    try {
                        // Verify user exists and belongs to company
                        User user = userRepository.findById(task.getAssignedTo()).orElse(null);
                        
                        if (user != null && user.getCompanyId().equals(companyId)) {
                            // Create project member
                            ProjectMember member = ProjectMember.builder()
                                .companyId(companyId)
                                .project(project)
                                .user(user)
                                .build();
                            
                            projectMemberRepository.save(member);
                            addedCount++;
                            log.info("Auto-added member {} from existing task assignment", user.getId());
                        }
                    } catch (Exception e) {
                        log.warn("Failed to add member for task {}: {}", task.getId(), e.getMessage());
                    }
                }
            }
        }
        
        log.info("Sync completed. Added {} members to project {}", addedCount, projectId);
        return addedCount;
    }

    private ProjectMemberResponse toResponse(ProjectMember member) {
        User user = member.getUser();
        
        return ProjectMemberResponse.builder()
            .id(member.getId())
            .userId(user.getId())
            .userName(user.getName())
            .userEmail(user.getEmail())
            .userDesignation(user.getDesignation())
            .userRole(user.getRole().name())
            .roleInProject(member.getRoleInProject())
            .createdAt(member.getCreatedAt())
            .build();
    }
}

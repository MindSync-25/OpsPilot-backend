package com.itops.service;

import com.itops.domain.Team;
import com.itops.domain.User;
import com.itops.dto.NotificationType;
import com.itops.dto.TeamRequest;
import com.itops.dto.TeamResponse;
import com.itops.repository.TeamRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public List<TeamResponse> getAllTeams(UUID companyId, UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        List<Team> teams;

        switch (currentUser.getRole()) {
            case TOP_USER:
                teams = teamRepository.findByCompanyId(companyId);
                break;

            case SUPER_USER:
                teams = teamRepository.findByCreatedByUserId(currentUserId);
                if (currentUser.getTeamId() != null) {
                    teamRepository.findById(currentUser.getTeamId())
                            .ifPresent(teams::add);
                }
                break;

            case ADMIN:
            case USER:
                teams = currentUser.getTeamId() != null
                    ? teamRepository.findById(currentUser.getTeamId())
                            .map(List::of)
                            .orElse(List.of())
                    : List.of();
                break;

            default:
                teams = List.of();
        }

        return teams.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public TeamResponse createTeam(TeamRequest request, UUID companyId, UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        if (currentUser.getRole() != User.UserRole.TOP_USER &&
            currentUser.getRole() != User.UserRole.SUPER_USER) {
            throw new RuntimeException("Only TOP_USER and SUPER_USER can create teams");
        }

        Team team = Team.builder()
                .name(request.getName())
                .leadUserId(request.getLeadUserId())
                .createdByUserId(currentUserId)
                .build();
        team.setCompanyId(companyId);

        Team saved = teamRepository.save(team);

        // If a team lead is specified, assign them to this team
        if (request.getLeadUserId() != null) {
            User leadUser = userRepository.findById(request.getLeadUserId())
                    .orElseThrow(() -> new RuntimeException("Team lead user not found"));
            leadUser.setTeamId(saved.getId());
            userRepository.save(leadUser);
            
            // Notify team lead
            notificationService.createNotification(
                    request.getLeadUserId(),
                    companyId,
                    NotificationType.TEAM_MEMBER_ADDED,
                    "Team Lead Assignment",
                    "You have been assigned as team lead for team: " + saved.getName(),
                    "TEAM",
                    saved.getId(),
                    currentUserId
            );
        }

        return toResponse(saved);
    }

    public TeamResponse updateTeam(UUID teamId, TeamRequest request, UUID companyId, UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (!team.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Team not found in your company");
        }

        boolean canUpdate = currentUser.getRole() == User.UserRole.TOP_USER ||
                (currentUser.getRole() == User.UserRole.SUPER_USER && team.getCreatedByUserId().equals(currentUserId));

        if (!canUpdate) {
            throw new RuntimeException("You do not have permission to update this team");
        }

        // Handle team lead change
        UUID oldLeadId = team.getLeadUserId();
        UUID newLeadId = request.getLeadUserId();

        team.setName(request.getName());
        team.setLeadUserId(newLeadId);

        Team updated = teamRepository.save(team);

        // If team lead changed, update user assignments
        if (newLeadId != null && !newLeadId.equals(oldLeadId)) {
            User newLead = userRepository.findById(newLeadId)
                    .orElseThrow(() -> new RuntimeException("Team lead user not found"));
            newLead.setTeamId(teamId);
            userRepository.save(newLead);
            
            // Notify new team lead
            notificationService.createNotification(
                    newLeadId,
                    companyId,
                    NotificationType.TEAM_MEMBER_ADDED,
                    "Team Lead Assignment",
                    "You have been assigned as team lead for team: " + updated.getName(),
                    "TEAM",
                    teamId,
                    currentUserId
            );
            
            // Notify old team lead if there was one
            if (oldLeadId != null) {
                notificationService.createNotification(
                        oldLeadId,
                        companyId,
                        NotificationType.TEAM_MEMBER_REMOVED,
                        "Team Lead Removed",
                        "You are no longer team lead for team: " + updated.getName(),
                        "TEAM",
                        teamId,
                        currentUserId
                );
            }
        }

        return toResponse(updated);
    }

    public void deleteTeam(UUID teamId, UUID companyId, UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));

        Team team = teamRepository.findById(teamId)
                .orElseThrow(() -> new RuntimeException("Team not found"));

        if (!team.getCompanyId().equals(companyId)) {
            throw new RuntimeException("Team not found in your company");
        }

        boolean canDelete = currentUser.getRole() == User.UserRole.TOP_USER ||
                (currentUser.getRole() == User.UserRole.SUPER_USER && team.getCreatedByUserId().equals(currentUserId));

        if (!canDelete) {
            throw new RuntimeException("You do not have permission to delete this team");
        }

        List<User> teamMembers = userRepository.findByTeamId(teamId);
        teamMembers.forEach(user -> {
            user.setTeamId(null);
            userRepository.save(user);
            
            // Notify member they were removed from team
            notificationService.createNotification(
                    user.getId(),
                    companyId,
                    NotificationType.TEAM_MEMBER_REMOVED,
                    "Team Removed",
                    "You have been removed from team: " + team.getName() + " (Team deleted)",
                    "TEAM",
                    teamId,
                    currentUserId
            );
        });

        teamRepository.delete(team);
    }

    private TeamResponse toResponse(Team team) {
        return TeamResponse.builder()
                .id(team.getId())
                .name(team.getName())
                .leadUserId(team.getLeadUserId())
                .createdByUserId(team.getCreatedByUserId())
                .createdAt(team.getCreatedAt())
                .build();
    }
}

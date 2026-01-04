package com.itops.service;

import com.itops.domain.Team;
import com.itops.domain.User;
import com.itops.repository.TeamRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service to determine which users a requester can view/manage based on their role.
 * Role-based scoping rules:
 * - TOP_USER: Can see all users in company
 * - SUPER_USER: Can see only users in teams they created
 * - ADMIN: Can see only users in their own team
 * - USER: Can see only themselves
 * - CLIENT: No access (403)
 */
@Service
@RequiredArgsConstructor
public class OrgScopeService {
    private final UserRepository userRepository;
    private final TeamRepository teamRepository;

    /**
     * Get list of user IDs that the requester is allowed to access.
     *
     * @param requesterId   The ID of the user making the request
     * @param requesterRole The role of the requester (TOP_USER, SUPER_USER, ADMIN, USER)
     * @param companyId     The company ID for scoping
     * @return Set of user IDs the requester can access
     */
    public Set<UUID> getAllowedUserIds(UUID requesterId, String requesterRole, UUID companyId) {
        switch (requesterRole) {
            case "TOP_USER":
                // TOP_USER can see all users in the company (except CLIENT role if desired)
                return userRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
                        .stream()
                        .filter(user -> user.getRole() != User.UserRole.CLIENT)
                        .map(User::getId)
                        .collect(Collectors.toSet());

            case "SUPER_USER":
                // SUPER_USER can see users in teams they created
                return getUsersInSuperUserTeams(requesterId, companyId);

            case "ADMIN":
                // ADMIN can see users in their own team
                return getUsersInAdminTeam(requesterId, companyId);

            case "USER":
                // USER can only see themselves
                return Set.of(requesterId);

            default:
                // CLIENT or unknown roles get empty set
                return Collections.emptySet();
        }
    }

    /**
     * Get users in teams created by a SUPER_USER.
     * SUPER_USER sees ADMIN and USER roles in their teams, but NOT other SUPER_USERs.
     */
    private Set<UUID> getUsersInSuperUserTeams(UUID superUserId, UUID companyId) {
        // Find all teams created by this SUPER_USER
        List<Team> teams = teamRepository.findByCreatedByUserIdAndDeletedAtIsNull(superUserId);
        
        System.out.println("=== SUPER_USER TEAM SCOPING DEBUG ===");
        System.out.println("SUPER_USER ID: " + superUserId);
        System.out.println("Teams created by this SUPER_USER: " + teams.size());
        
        Set<UUID> userIds = new HashSet<>();
        userIds.add(superUserId); // Include self
        
        for (Team team : teams) {
            System.out.println("Team: " + team.getName() + " (ID: " + team.getId() + ")");
            
            // Get all users assigned to this team
            List<User> teamMembers = userRepository.findByTeamIdAndDeletedAtIsNull(team.getId());
            System.out.println("  Total team members: " + teamMembers.size());
            
            // First, show ALL team members and their roles
            for (User user : teamMembers) {
                System.out.println("    ALL MEMBERS - " + user.getName() + " (Role: " + user.getRole() + ", ID: " + user.getId() + ")");
            }
            
            // Add users who are ADMIN or USER (not other SUPER_USERs)
            List<User> eligibleMembers = teamMembers.stream()
                    .filter(user -> {
                        User.UserRole role = user.getRole();
                        System.out.println("      Checking role: " + role + " equals ADMIN? " + (role == User.UserRole.ADMIN) + ", equals USER? " + (role == User.UserRole.USER));
                        return role == User.UserRole.ADMIN || role == User.UserRole.USER;
                    })
                    .toList();
            
            System.out.println("  Eligible members (ADMIN/USER): " + eligibleMembers.size());
            for (User user : eligibleMembers) {
                System.out.println("    ELIGIBLE - " + user.getName() + " (" + user.getRole() + ")");
            }
            
            eligibleMembers.stream()
                    .map(User::getId)
                    .forEach(userIds::add);
        }
        
        System.out.println("Total allowed user IDs (including self): " + userIds.size());
        System.out.println("=====================================");
        
        return userIds;
    }

    /**
     * Get users in the same team as an ADMIN.
     */
    private Set<UUID> getUsersInAdminTeam(UUID adminId, UUID companyId) {
        User admin = userRepository.findById(adminId).orElse(null);
        if (admin == null || admin.getTeamId() == null) {
            return Set.of(adminId); // Only themselves if no team
        }
        
        // Get all users in the same team
        List<User> teamMembers = userRepository.findByTeamIdAndDeletedAtIsNull(admin.getTeamId());
        
        Set<UUID> userIds = new HashSet<>();
        userIds.add(adminId); // Include self
        
        // Add all team members (typically other ADMINs and USERs)
        teamMembers.stream()
                .map(User::getId)
                .forEach(userIds::add);
        
        return userIds;
    }

    /**
     * Check if requester can access a specific user.
     */
    public boolean canAccessUser(UUID requesterId, String requesterRole, UUID targetUserId, UUID companyId) {
        Set<UUID> allowedUserIds = getAllowedUserIds(requesterId, requesterRole, companyId);
        return allowedUserIds.contains(targetUserId);
    }
    
    /**
     * Get allowed user IDs for approvals (excluding the requester themselves).
     * Used for timesheet/leave approvals where users shouldn't approve their own requests.
     */
    public Set<UUID> getAllowedUserIdsForApprovals(UUID requesterId, String requesterRole, UUID companyId) {
        Set<UUID> allowedUserIds = getAllowedUserIds(requesterId, requesterRole, companyId);
        // Remove requester from the set - can't approve own submissions
        allowedUserIds.remove(requesterId);
        return allowedUserIds;
    }
}

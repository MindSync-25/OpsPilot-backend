package com.itops.service;

import com.itops.domain.User;
import com.itops.dto.CreateUserRequest;
import com.itops.dto.UserResponse;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.UserRepository;
import com.itops.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final TeamRepository teamRepository;
    private final PasswordEncoder passwordEncoder;
    private final SubscriptionGuard subscriptionGuard;

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsers(UUID companyId, UUID currentUserId) {
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        List<User> users;
        
        switch (currentUser.getRole()) {
            case TOP_USER:
                // TOP_USER sees all users in the company
                users = userRepository.findByCompanyId(companyId);
                break;
                
            case SUPER_USER:
                // SUPER_USER sees users they created + users in their team
                users = getVisibleUsersForSuperUser(currentUserId, currentUser.getTeamId());
                break;

            case ADMIN:
                // ADMIN sees users in their team
                if (currentUser.getTeamId() != null) {
                    users = userRepository.findByTeamId(currentUser.getTeamId());
                } else {
                    users = new ArrayList<>();
                }
                break;

            case USER:
                // ADMIN and USER see only users in their team
                if (currentUser.getTeamId() != null) {
                    users = userRepository.findByTeamId(currentUser.getTeamId());
                } else {
                    users = new ArrayList<>();
                }
                break;
                
            default:
                users = new ArrayList<>();
                break;
        }
        
        return users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponse> getAllUsersForAssignment(UUID companyId, UUID currentUserId) {
        // Use the same filtering logic as getAllUsers
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        System.out.println("=== getAllUsersForAssignment DEBUG ===");
        System.out.println("Current User: " + currentUser.getName() + " (ID: " + currentUserId + ")");
        System.out.println("Current User Role: " + currentUser.getRole());
        System.out.println("Current User Team ID: " + currentUser.getTeamId());

        List<User> users;
        
        switch (currentUser.getRole()) {
            case TOP_USER:
                // TOP_USER sees all users in the company
                users = userRepository.findByCompanyId(companyId);
                break;
                
            case SUPER_USER:
                // SUPER_USER sees users they created + users in their team
                users = getVisibleUsersForSuperUser(currentUserId, currentUser.getTeamId());
                break;

            case ADMIN:
                // ADMIN sees users in their team
                if (currentUser.getTeamId() != null) {
                    users = userRepository.findByTeamId(currentUser.getTeamId());
                } else {
                    users = new ArrayList<>();
                }
                break;

            case USER:
                // USER sees only users in their team
                if (currentUser.getTeamId() != null) {
                    users = userRepository.findByTeamId(currentUser.getTeamId());
                } else {
                    users = new ArrayList<>();
                }
                break;
                
            default:
                users = new ArrayList<>();
                break;
        }
        
        System.out.println("Users returned (count: " + users.size() + "):");
        for (User u : users) {
            System.out.println("  - " + u.getName() + " (Role: " + u.getRole() + ", Team: " + u.getTeamId() + ")");
        }
        System.out.println("=== END DEBUG ===");
        
        return users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private List<User> getVisibleUsersForSuperUser(UUID superUserId, UUID teamId) {
        // SUPER_USER sees all users in their hierarchy (recursively)
        Set<User> visibleUsers = new HashSet<>();
        collectUsersRecursively(superUserId, visibleUsers);
        return new ArrayList<>(visibleUsers);
    }

    private void collectUsersRecursively(UUID creatorId, Set<User> result) {
        List<User> directChildren = userRepository.findByCreatedByUserId(creatorId);
        for (User child : directChildren) {
            if (result.add(child)) {
                // Recursively collect users created by this child
                collectUsersRecursively(child.getId(), result);
            }
        }
    }

    public List<UserResponse> getUsersForMentions(UUID companyId, UUID currentUserId) {
        // For mentions, everyone can see all active users in the company
        System.out.println("=== getUsersForMentions DEBUG ===");
        System.out.println("Fetching all active users for company: " + companyId);
        
        List<User> users = userRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        
        System.out.println("Users for mentions (count: " + users.size() + "):");
        for (User u : users) {
            System.out.println("  - " + u.getName() + " (Role: " + u.getRole() + ", Team: " + u.getTeamId() + ", Deleted: " + u.getDeletedAt() + ")");
        }
        System.out.println("=== END getUsersForMentions DEBUG ===");
        
        return users.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public UserResponse createUser(CreateUserRequest request, UUID companyId, UUID currentUserId) {
        // Enforce subscription limits
        subscriptionGuard.enforceUserCreation(companyId);
        
        // Check for globally unique email
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already exists");
        }
        
        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Current user not found"));

        User.UserRole requestedRole = User.UserRole.valueOf(request.getRole());
        
        // Validate permission to create user with requested role
        validateUserCreationPermission(currentUser.getRole(), requestedRole);
        
        // Determine team assignment
        UUID assignedTeamId = determineTeamAssignment(
                request.getTeamId(), 
                currentUser.getRole(), 
                currentUser.getTeamId(), 
                currentUserId
        );
        
        // Create new user
        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(requestedRole)
                .teamId(assignedTeamId)
                .designation(request.getDesignation())
                .hourlyRate(request.getHourlyRate())
                .isActive(true)
                .createdByUserId(currentUserId)
                .managerUserId(request.getManagerUserId())
                .build();
        
        user.setCompanyId(companyId);
        
        User saved = userRepository.save(user);
        return mapToResponse(saved);
    }

    private void validateUserCreationPermission(User.UserRole creatorRole, User.UserRole requestedRole) {
        switch (creatorRole) {
            case TOP_USER:
                // TOP_USER can create any role
                break;
                
            case SUPER_USER:
                // SUPER_USER can create ADMIN and USER
                if (requestedRole == User.UserRole.TOP_USER || requestedRole == User.UserRole.SUPER_USER) {
                    throw new IllegalArgumentException("SUPER_USER cannot create TOP_USER or SUPER_USER");
                }
                break;
                
            case ADMIN:
                // ADMIN can only create USER
                if (requestedRole != User.UserRole.USER) {
                    throw new IllegalArgumentException("ADMIN can only create USER role");
                }
                break;
                
            default:
                throw new IllegalArgumentException("Insufficient permissions to create users");
        }
    }

    private UUID determineTeamAssignment(UUID requestedTeamId, User.UserRole creatorRole, 
                                        UUID creatorTeamId, UUID creatorUserId) {
        switch (creatorRole) {
            case TOP_USER:
                // TOP_USER can assign to any team or leave unassigned
                return requestedTeamId;
                
            case SUPER_USER:
                // SUPER_USER can assign to their own team or leave unassigned
                if (requestedTeamId != null && !requestedTeamId.equals(creatorTeamId)) {
                    throw new IllegalArgumentException("SUPER_USER can only assign users to their own team");
                }
                return requestedTeamId != null ? requestedTeamId : creatorTeamId;
                
            case ADMIN:
                // ADMIN must assign to their own team
                return creatorTeamId;
                
            default:
                throw new IllegalArgumentException("Insufficient permissions to assign team");
        }
    }

    @Transactional
    public UserResponse updateUser(UUID id, CreateUserRequest request, UUID companyId) {
        User user = userRepository.findById(id)
                .filter(u -> u.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        user.setName(request.getName());
        user.setEmail(request.getEmail());
        
        if (request.getPassword() != null && !request.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        
        if (request.getRole() != null) {
            user.setRole(User.UserRole.valueOf(request.getRole()));
        }
        
        user.setTeamId(request.getTeamId());
        user.setDesignation(request.getDesignation());
        user.setManagerUserId(request.getManagerUserId());
        user.setHourlyRate(request.getHourlyRate());

        User updated = userRepository.save(user);
        return mapToResponse(updated);
    }

    @Transactional
    public void deleteUser(UUID id, UUID companyId) {
        User user = userRepository.findById(id)
                .filter(u -> u.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        userRepository.delete(user);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(UUID userId, UUID companyId) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        return mapToResponse(user);
    }

    @Transactional
    public UserResponse updateCurrentUserProfile(UUID userId, UUID companyId, com.itops.dto.UpdateUserProfileRequest request) {
        User user = userRepository.findById(userId)
                .filter(u -> u.getCompanyId().equals(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        if (request.getName() != null && !request.getName().isEmpty()) {
            user.setName(request.getName());
        }
        
        if (request.getEmail() != null && !request.getEmail().isEmpty()) {
            // Check if email is already taken by another user
            userRepository.findByEmail(request.getEmail())
                    .ifPresent(existingUser -> {
                        if (!existingUser.getId().equals(userId)) {
                            throw new IllegalArgumentException("Email already exists");
                        }
                    });
            user.setEmail(request.getEmail());
        }
        
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        
        if (request.getDesignation() != null) {
            user.setDesignation(request.getDesignation());
        }
        
        User updated = userRepository.save(user);
        return mapToResponse(updated);
    }

    private UserResponse mapToResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole().name())
                .teamId(user.getTeamId())
                .designation(user.getDesignation())
                .hourlyRate(user.getHourlyRate())
                .isActive(user.getIsActive())
                .createdAt(user.getCreatedAt())
                .companyId(user.getCompanyId())
                .createdByUserId(user.getCreatedByUserId())
                .managerUserId(user.getManagerUserId())
                .build();
    }
}
package com.itops.service;

import com.itops.domain.LeaveRequest;
import com.itops.domain.User;
import com.itops.dto.CreateLeaveRequest;
import com.itops.dto.LeaveResponse;
import com.itops.dto.NotificationType;
import com.itops.dto.UpdateLeaveStatusRequest;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.LeaveRequestRepository;
import com.itops.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveService {
    private final LeaveRequestRepository leaveRequestRepository;
    private final UserRepository userRepository;
    private final OrgScopeService orgScopeService;
    private final NotificationService notificationService;

    /**
     * Get all leave requests for current user.
     */
    public List<LeaveResponse> getMyLeaveRequests(UUID userId) {
        List<LeaveRequest> requests = leaveRequestRepository.findByUserIdAndDeletedAtIsNull(userId);
        return requests.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Create a new leave request.
     */
    @Transactional
    public LeaveResponse createLeaveRequest(UUID userId, UUID companyId, CreateLeaveRequest request) {
        // Validate dates
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("End date cannot be before start date");
        }
        
        LeaveRequest leaveRequest = LeaveRequest.builder()
                .companyId(companyId)
                .userId(userId)
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .type(request.getType())
                .status("PENDING")
                .reason(request.getReason())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        LeaveRequest saved = leaveRequestRepository.save(leaveRequest);
        
        // Notify approvers - only direct manager and TOP_USER
        User requestingUser = userRepository.findById(userId).orElse(null);
        if (requestingUser != null) {
            List<User> approvers = new ArrayList<>();
            
            // Add direct manager if exists
            if (requestingUser.getManagerUserId() != null) {
                userRepository.findById(requestingUser.getManagerUserId()).ifPresent(approvers::add);
            }
            
            // Add all TOP_USER roles
            List<User> topUsers = userRepository.findByCompanyId(companyId).stream()
                    .filter(u -> u.getRole() == User.UserRole.TOP_USER)
                    .toList();
            approvers.addAll(topUsers);
            
            String userName = requestingUser.getName();
            
            for (User approver : approvers) {
                notificationService.createNotification(
                        approver.getId(),
                        companyId,
                        NotificationType.LEAVE_REQUEST_CREATED,
                        "New Leave Request",
                        userName + " requested leave from " + request.getStartDate() + " to " + request.getEndDate(),
                        "LEAVE",
                        saved.getId(),
                        userId
                );
            }
        }
        
        return toResponse(saved);
    }

    /**
     * Get all leave requests for allowed users (role-based scoping).
     */
    public List<LeaveResponse> getAllLeaveRequests(UUID requesterId, String requesterRole, UUID companyId,
                                                     String status, LocalDate fromDate, LocalDate toDate, UUID userId) {
        // Get allowed user IDs based on role (excluding requester for approvals)
        Set<UUID> allowedUserIds = orgScopeService.getAllowedUserIdsForApprovals(requesterId, requesterRole, companyId);
        
        if (allowedUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Filter by userId if specified
        final Set<UUID> finalAllowedUserIds;
        if (userId != null) {
            if (!allowedUserIds.contains(userId)) {
                throw new IllegalArgumentException("You do not have permission to view this user's leave requests");
            }
            finalAllowedUserIds = Set.of(userId);
        } else {
            finalAllowedUserIds = allowedUserIds;
        }
        
        List<LeaveRequest> requests;
        
        if (fromDate != null && toDate != null) {
            requests = leaveRequestRepository.findByCompanyIdAndDateRange(companyId, fromDate, toDate);
        } else if (status != null) {
            requests = leaveRequestRepository.findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status);
        } else {
            requests = leaveRequestRepository.findByCompanyIdAndUserIdIn(companyId, new ArrayList<>(finalAllowedUserIds));
        }
        
        // Filter by allowed user IDs
        return requests.stream()
                .filter(r -> finalAllowedUserIds.contains(r.getUserId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    /**
     * Update leave request status (approve/reject/cancel).
     */
    @Transactional
    public LeaveResponse updateLeaveStatus(UUID leaveId, UpdateLeaveStatusRequest request,
                                             UUID requesterId, String requesterRole, UUID companyId) {
        LeaveRequest leaveRequest = leaveRequestRepository.findById(leaveId)
                .orElseThrow(() -> new ResourceNotFoundException("Leave request not found"));
        
        if (!leaveRequest.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("Leave request not found in your company");
        }
        
        // Handle CANCEL - user can only cancel their own pending request
        if ("CANCELLED".equals(request.getStatus())) {
            if (!leaveRequest.getUserId().equals(requesterId)) {
                throw new IllegalArgumentException("You can only cancel your own leave request");
            }
            if (!"PENDING".equals(leaveRequest.getStatus())) {
                throw new IllegalStateException("Only pending leave requests can be cancelled");
            }
            
            leaveRequest.setStatus("CANCELLED");
            leaveRequest.setDecisionNote(request.getDecisionNote());
            leaveRequest.setDecidedAt(LocalDateTime.now());
            leaveRequestRepository.save(leaveRequest);
            return toResponse(leaveRequest);
        }
        
        // Handle APPROVE/REJECT - check if requester can approve this user's request
        if (!orgScopeService.canAccessUser(requesterId, requesterRole, leaveRequest.getUserId(), companyId)) {
            throw new IllegalArgumentException("You do not have permission to review this leave request");
        }
        
        // SUPER_USER cannot approve other SUPER_USER's leave
        User targetUser = userRepository.findById(leaveRequest.getUserId()).orElse(null);
        if ("SUPER_USER".equals(requesterRole) && targetUser != null && "SUPER_USER".equals(targetUser.getRole())) {
            throw new IllegalArgumentException("SUPER_USER cannot approve another SUPER_USER's leave request");
        }
        
        if (!"PENDING".equals(leaveRequest.getStatus())) {
            throw new IllegalStateException("Only pending leave requests can be reviewed");
        }
        
        leaveRequest.setStatus(request.getStatus());
        leaveRequest.setApproverId(requesterId);
        leaveRequest.setDecisionNote(request.getDecisionNote());
        leaveRequest.setDecidedAt(LocalDateTime.now());
        
        leaveRequestRepository.save(leaveRequest);
        
        // Notify the leave requester
        String notificationType = "APPROVED".equals(request.getStatus()) ? 
                NotificationType.LEAVE_REQUEST_APPROVED : NotificationType.LEAVE_REQUEST_REJECTED;
        String title = "APPROVED".equals(request.getStatus()) ? 
                "Leave Request Approved" : "Leave Request Rejected";
        String message = "APPROVED".equals(request.getStatus()) ? 
                "Your leave request from " + leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate() + " has been approved" :
                "Your leave request from " + leaveRequest.getStartDate() + " to " + leaveRequest.getEndDate() + " has been rejected" +
                (request.getDecisionNote() != null ? ": " + request.getDecisionNote() : "");
        
        notificationService.createNotification(
                leaveRequest.getUserId(),
                companyId,
                notificationType,
                title,
                message,
                "LEAVE",
                leaveRequest.getId(),
                requesterId
        );
        
        return toResponse(leaveRequest);
    }

    /**
     * Convert LeaveRequest entity to response DTO.
     */
    private LeaveResponse toResponse(LeaveRequest leaveRequest) {
        String userName = userRepository.findById(leaveRequest.getUserId())
                .map(User::getName)
                .orElse(null);
        
        String approverName = null;
        if (leaveRequest.getApproverId() != null) {
            approverName = userRepository.findById(leaveRequest.getApproverId())
                    .map(User::getName)
                    .orElse(null);
        }
        
        return LeaveResponse.builder()
                .id(leaveRequest.getId())
                .userId(leaveRequest.getUserId())
                .userName(userName)
                .startDate(leaveRequest.getStartDate())
                .endDate(leaveRequest.getEndDate())
                .type(leaveRequest.getType())
                .status(leaveRequest.getStatus())
                .reason(leaveRequest.getReason())
                .approverId(leaveRequest.getApproverId())
                .approverName(approverName)
                .decisionNote(leaveRequest.getDecisionNote())
                .decidedAt(leaveRequest.getDecidedAt())
                .createdAt(leaveRequest.getCreatedAt())
                .updatedAt(leaveRequest.getUpdatedAt())
                .build();
    }
}

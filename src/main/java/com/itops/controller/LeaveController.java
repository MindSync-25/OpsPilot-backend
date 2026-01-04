package com.itops.controller;

import com.itops.dto.CreateLeaveRequest;
import com.itops.dto.LeaveResponse;
import com.itops.dto.UpdateLeaveStatusRequest;
import com.itops.service.LeaveService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/leave")
@RequiredArgsConstructor
public class LeaveController {
    private final LeaveService leaveService;

    /**
     * GET /leave/me
     * Get all my leave requests.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<List<LeaveResponse>> getMyLeaveRequests(Authentication authentication) {
        UUID userId = extractUserId(authentication);
        List<LeaveResponse> requests = leaveService.getMyLeaveRequests(userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * POST /leave/me
     * Create a new leave request.
     */
    @PostMapping("/me")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<LeaveResponse> createLeaveRequest(
            @Valid @RequestBody CreateLeaveRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        LeaveResponse leave = leaveService.createLeaveRequest(userId, companyId, request);
        return ResponseEntity.ok(leave);
    }

    /**
     * GET /leave
     * Get all leave requests for allowed users.
     * Query params: status, fromDate, toDate, userId (optional)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<List<LeaveResponse>> getAllLeaveRequests(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) UUID userId,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        String requesterRole = extractRole(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        List<LeaveResponse> requests = leaveService.getAllLeaveRequests(
                requesterId, requesterRole, companyId, status, fromDate, toDate, userId);
        return ResponseEntity.ok(requests);
    }

    /**
     * PATCH /leave/{id}/status
     * Approve, reject, or cancel a leave request.
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<LeaveResponse> updateLeaveStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLeaveStatusRequest request,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        String requesterRole = extractRole(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        LeaveResponse leave = leaveService.updateLeaveStatus(id, request, requesterId, requesterRole, companyId);
        return ResponseEntity.ok(leave);
    }

    // Helper methods to extract claims from JWT
    private UUID extractUserId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map claims && claims.containsKey("userId")) {
            return UUID.fromString(claims.get("userId").toString());
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }

    private UUID extractCompanyId(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map claims && claims.containsKey("companyId")) {
            return UUID.fromString(claims.get("companyId").toString());
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }

    private String extractRole(Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof Map claims && claims.containsKey("role")) {
            return claims.get("role").toString();
        }
        throw new IllegalArgumentException("Invalid authentication principal");
    }
}

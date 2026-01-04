package com.itops.controller;

import com.itops.dto.*;
import com.itops.service.TimesheetService;
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
@RequestMapping("/timesheets")
@RequiredArgsConstructor
public class TimesheetController {
    private final TimesheetService timesheetService;

    /**
     * GET /timesheets/me?weekStart=YYYY-MM-DD
     * Returns my timesheet snapshot + derived totals for a specific week.
     */
    @GetMapping("/me")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<TimesheetResponse> getMyTimesheet(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        System.out.println(">>> GET /timesheets/me - weekStart: " + weekStart + ", userId: " + userId);
        TimesheetResponse timesheet = timesheetService.getOrCreateTimesheet(userId, weekStart, companyId);
        return ResponseEntity.ok(timesheet);
    }

    /**
     * POST /timesheets/me/submit
     * Submit my timesheet for approval.
     */
    @PostMapping("/me/submit")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<TimesheetResponse> submitMyTimesheet(
            @Valid @RequestBody SubmitTimesheetRequest request,
            Authentication authentication) {
        UUID userId = extractUserId(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        TimesheetResponse timesheet = timesheetService.submitTimesheet(userId, request.getWeekStart(), companyId);
        return ResponseEntity.ok(timesheet);
    }

    /**
     * GET /timesheets
     * Returns timesheets for allowed scopeUsers.
     * Query params: weekStart, status, userId (optional)
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<List<TimesheetResponse>> getTimesheets(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID userId,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        String requesterRole = extractRole(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        System.out.println(">>> GET /timesheets - requesterId: " + requesterId + ", role: " + requesterRole + ", status: " + status + ", userId: " + userId);
        
        List<TimesheetResponse> timesheets = timesheetService.getTimesheets(
                requesterId, requesterRole, companyId, weekStart, status, userId);
        
        System.out.println(">>> Returning " + timesheets.size() + " timesheets");
        return ResponseEntity.ok(timesheets);
    }

    /**
     * GET /timesheets/{id}
     * Get a specific timesheet by ID (with access control)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN', 'USER')")
    public ResponseEntity<TimesheetResponse> getTimesheetById(
            @PathVariable UUID id,
            Authentication authentication) {
        UUID requesterId = extractUserId(authentication);
        String requesterRole = extractRole(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        TimesheetResponse timesheet = timesheetService.getTimesheetById(id, requesterId, requesterRole, companyId);
        return ResponseEntity.ok(timesheet);
    }

    /**
     * PATCH /timesheets/{id}/review
     * Approve or reject a timesheet.
     */
    @PatchMapping("/{id}/review")
    @PreAuthorize("hasAnyRole('TOP_USER', 'SUPER_USER', 'ADMIN')")
    public ResponseEntity<TimesheetResponse> reviewTimesheet(
            @PathVariable UUID id,
            @Valid @RequestBody ReviewTimesheetRequest request,
            Authentication authentication) {
        UUID reviewerId = extractUserId(authentication);
        String reviewerRole = extractRole(authentication);
        UUID companyId = extractCompanyId(authentication);
        
        TimesheetResponse timesheet = timesheetService.reviewTimesheet(
                id, request, reviewerId, reviewerRole, companyId);
        return ResponseEntity.ok(timesheet);
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

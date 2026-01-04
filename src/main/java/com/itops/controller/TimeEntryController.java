package com.itops.controller;

import com.itops.dto.CreateManualTimeEntryRequest;
import com.itops.dto.StartTimerRequest;
import com.itops.dto.TimeEntryResponse;
import com.itops.dto.UpdateTimeEntryRequest;
import com.itops.security.JwtUtil;
import com.itops.service.TimeEntryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/time-entries")
@RequiredArgsConstructor
public class TimeEntryController {

    private final TimeEntryService timeEntryService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<TimeEntryResponse>> getAllTimeEntries(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Boolean billable,
            HttpServletRequest request
    ) {
        UUID companyId = getCompanyIdFromRequest(request);
        UUID requestUserId = getUserIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        // For projectId queries, managers can see all team time entries
        // For personal time tracking (no projectId), users only see their own
        UUID effectiveUserId = (projectId != null && ("TOP_USER".equals(userRole) || "SUPER_USER".equals(userRole) || "ADMIN".equals(userRole))) 
            ? userId  // Managers can specify userId or null to see all
            : requestUserId; // Regular users see only their own
        
        List<TimeEntryResponse> entries = timeEntryService.getAllTimeEntries(
                companyId, projectId, effectiveUserId, fromDate, toDate, billable
        );
        return ResponseEntity.ok(entries);
    }

    @GetMapping("/active")
    public ResponseEntity<TimeEntryResponse> getActiveTimer(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        Optional<TimeEntryResponse> activeTimer = timeEntryService.getActiveTimer(userId);
        return activeTimer.map(ResponseEntity::ok)
                .orElse(ResponseEntity.noContent().build());
    }

    @PostMapping("/start")
    public ResponseEntity<TimeEntryResponse> startTimer(
            @Valid @RequestBody StartTimerRequest startRequest,
            HttpServletRequest request
    ) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        TimeEntryResponse response = timeEntryService.startTimer(startRequest, userId, companyId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/stop")
    public ResponseEntity<TimeEntryResponse> stopTimer(HttpServletRequest request) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        TimeEntryResponse response = timeEntryService.stopTimer(userId, companyId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/manual")
    public ResponseEntity<TimeEntryResponse> createManualEntry(
            @Valid @RequestBody CreateManualTimeEntryRequest createRequest,
            HttpServletRequest request
    ) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        TimeEntryResponse response = timeEntryService.createManualEntry(createRequest, userId, companyId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TimeEntryResponse> updateEntry(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateTimeEntryRequest updateRequest,
            HttpServletRequest request
    ) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        boolean isAdmin = "TOP_USER".equals(userRole) || "SUPER_USER".equals(userRole) || "ADMIN".equals(userRole);
        TimeEntryResponse response = timeEntryService.updateEntry(id, updateRequest, userId, companyId, isAdmin);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEntry(
            @PathVariable UUID id,
            HttpServletRequest request
    ) {
        UUID userId = getUserIdFromRequest(request);
        UUID companyId = getCompanyIdFromRequest(request);
        String userRole = getRoleFromRequest(request);
        
        if ("CLIENT".equals(userRole)) {
            return ResponseEntity.status(403).build();
        }
        
        boolean isAdmin = "TOP_USER".equals(userRole) || "SUPER_USER".equals(userRole) || "ADMIN".equals(userRole);
        timeEntryService.deleteEntry(id, userId, companyId, isAdmin);
        return ResponseEntity.noContent().build();
    }

    private UUID getCompanyIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getCompanyIdFromToken(token);
    }

    private UUID getUserIdFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getUserIdFromToken(token);
    }

    private String getRoleFromRequest(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.getRoleFromToken(token);
    }
}

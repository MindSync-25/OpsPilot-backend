package com.itops.service;

import com.itops.domain.Timesheet;
import com.itops.domain.TimeEntry;
import com.itops.domain.User;
import com.itops.domain.Project;
import com.itops.dto.NotificationType;
import com.itops.dto.TimesheetResponse;
import com.itops.dto.ReviewTimesheetRequest;
import com.itops.exception.ResourceNotFoundException;
import com.itops.repository.TimesheetRepository;
import com.itops.repository.TimeEntryRepository;
import com.itops.repository.UserRepository;
import com.itops.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TimesheetService {
    private final TimesheetRepository timesheetRepository;
    private final TimeEntryRepository timeEntryRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final OrgScopeService orgScopeService;
    private final NotificationService notificationService;

    /**
     * Get or create timesheet for a user and week.
     * Calculates totals from time_entries.
     */
    @Transactional
    public TimesheetResponse getOrCreateTimesheet(UUID userId, LocalDate weekStart, UUID companyId) {
        // Ensure weekStart is a Monday
        LocalDate monday = weekStart.minusDays(weekStart.getDayOfWeek().getValue() - 1);
        
        Timesheet timesheet = timesheetRepository.findByUserIdAndWeekStartAndDeletedAtIsNull(userId, monday)
                .orElseGet(() -> createTimesheetForWeek(userId, monday, companyId));
        
        // Recalculate totals from time_entries
        updateTimesheetTotals(timesheet);
        timesheetRepository.save(timesheet);
        
        return toResponse(timesheet);
    }

    /**
     * Submit timesheet for approval.
     */
    @Transactional
    public TimesheetResponse submitTimesheet(UUID userId, LocalDate weekStart, UUID companyId) {
        LocalDate monday = weekStart.minusDays(weekStart.getDayOfWeek().getValue() - 1);
        
        Timesheet timesheet = timesheetRepository.findByUserIdAndWeekStartAndDeletedAtIsNull(userId, monday)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found for week: " + monday));
        
        if (!timesheet.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot submit another user's timesheet");
        }
        
        if ("SUBMITTED".equals(timesheet.getStatus()) || "APPROVED".equals(timesheet.getStatus())) {
            throw new IllegalStateException("Timesheet already submitted or approved");
        }
        
        timesheet.setStatus("SUBMITTED");
        timesheet.setSubmittedAt(LocalDateTime.now());
        timesheetRepository.save(timesheet);
        
        // Notify approvers - only direct manager and TOP_USER
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            List<User> approvers = new ArrayList<>();
            
            // Add direct manager if exists
            if (user.getManagerUserId() != null) {
                userRepository.findById(user.getManagerUserId()).ifPresent(approvers::add);
            }
            
            // Add all TOP_USER roles
            List<User> topUsers = userRepository.findByCompanyId(companyId).stream()
                    .filter(u -> u.getRole() == User.UserRole.TOP_USER)
                    .toList();
            approvers.addAll(topUsers);
            
            for (User approver : approvers) {
                notificationService.createNotification(
                        approver.getId(),
                        companyId,
                        NotificationType.TIMESHEET_SUBMITTED,
                        "Timesheet Submitted",
                        user.getName() + " submitted their timesheet for week " + monday,
                        "TIMESHEET",
                        timesheet.getId(),
                        userId
                );
            }
        }
        
        return toResponse(timesheet);
    }

    /**
     * Review (approve/reject) a timesheet.
     * Only allowed for TOP_USER, SUPER_USER, ADMIN within their scope.
     */
    @Transactional
    public TimesheetResponse reviewTimesheet(UUID timesheetId, ReviewTimesheetRequest request, 
                                               UUID reviewerId, String reviewerRole, UUID companyId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found"));
        
        if (!timesheet.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("Timesheet not found in your company");
        }
        
        // Check if reviewer has permission to approve this user's timesheet
        if (!orgScopeService.canAccessUser(reviewerId, reviewerRole, timesheet.getUserId(), companyId)) {
            throw new IllegalArgumentException("You do not have permission to review this timesheet");
        }
        
        if (!"SUBMITTED".equals(timesheet.getStatus())) {
            throw new IllegalStateException("Only submitted timesheets can be reviewed");
        }
        
        timesheet.setStatus(request.getStatus());
        timesheet.setApprovedBy(reviewerId);
        timesheet.setApprovedAt(LocalDateTime.now());
        
        if ("REJECTED".equals(request.getStatus())) {
            timesheet.setRejectionReason(request.getRejectionReason());
        }
        
        timesheetRepository.save(timesheet);
        
        // Notify the timesheet owner
        String notificationType = "APPROVED".equals(request.getStatus()) ? 
                NotificationType.TIMESHEET_APPROVED : NotificationType.TIMESHEET_REJECTED;
        String title = "APPROVED".equals(request.getStatus()) ? 
                "Timesheet Approved" : "Timesheet Rejected";
        String message = "APPROVED".equals(request.getStatus()) ? 
                "Your timesheet for week " + timesheet.getWeekStart() + " has been approved" :
                "Your timesheet for week " + timesheet.getWeekStart() + " has been rejected" +
                (request.getRejectionReason() != null ? ": " + request.getRejectionReason() : "");
        
        notificationService.createNotification(
                timesheet.getUserId(),
                companyId,
                notificationType,
                title,
                message,
                "TIMESHEET",
                timesheet.getId(),
                reviewerId
        );
        
        return toResponse(timesheet);
    }

    /**
     * Get all timesheets for allowed users.
     * Filtered by role-based scope.
     */
    public List<TimesheetResponse> getTimesheets(UUID requesterId, String requesterRole, UUID companyId,
                                                   LocalDate weekStart, String status, UUID userId) {
        // Get allowed user IDs based on role (excluding requester for approvals)
        Set<UUID> allowedUserIds = orgScopeService.getAllowedUserIdsForApprovals(requesterId, requesterRole, companyId);
        
        System.out.println("=== TIMESHEET APPROVALS DEBUG ===");
        System.out.println("Requester ID: " + requesterId + ", Role: " + requesterRole);
        System.out.println("Allowed User IDs for Approvals: " + allowedUserIds);
        System.out.println("Status filter: " + status);
        System.out.println("UserId filter: " + userId);
        
        if (allowedUserIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        // Filter by userId if specified
        final Set<UUID> finalAllowedUserIds;
        if (userId != null) {
            if (!allowedUserIds.contains(userId)) {
                throw new IllegalArgumentException("You do not have permission to view this user's timesheets");
            }
            finalAllowedUserIds = Set.of(userId);
        } else {
            finalAllowedUserIds = allowedUserIds;
        }
        
        List<Timesheet> timesheets;
        
        if (weekStart != null && status != null) {
            LocalDate monday = weekStart.minusDays(weekStart.getDayOfWeek().getValue() - 1);
            timesheets = timesheetRepository.findByCompanyIdAndStatusAndWeekStart(companyId, status, monday);
        } else if (weekStart != null) {
            LocalDate monday = weekStart.minusDays(weekStart.getDayOfWeek().getValue() - 1);
            timesheets = timesheetRepository.findByCompanyIdAndWeekRange(companyId, monday, monday);
        } else if (status != null) {
            timesheets = timesheetRepository.findByCompanyIdAndStatusAndDeletedAtIsNull(companyId, status);
        } else {
            timesheets = timesheetRepository.findByCompanyIdAndUserIdIn(companyId, new ArrayList<>(finalAllowedUserIds));
        }
        
        System.out.println("Found " + timesheets.size() + " timesheets before filtering");
        
        // Filter by allowed user IDs
        List<TimesheetResponse> result = timesheets.stream()
                .filter(t -> finalAllowedUserIds.contains(t.getUserId()))
                .map(this::toResponse)
                .collect(Collectors.toList());
        
        System.out.println("Returning " + result.size() + " timesheets after filtering");
        System.out.println("=====================================");
        
        return result;
    }

    /**
     * Get a specific timesheet by ID with access control.
     */
    public TimesheetResponse getTimesheetById(UUID timesheetId, UUID requesterId, String requesterRole, UUID companyId) {
        Timesheet timesheet = timesheetRepository.findById(timesheetId)
                .orElseThrow(() -> new ResourceNotFoundException("Timesheet not found"));
        
        // Verify company
        if (!timesheet.getCompanyId().equals(companyId)) {
            throw new IllegalArgumentException("Timesheet not found in your company");
        }
        
        // Check if requester has permission to view this timesheet
        // Users can view their own, managers can view their subordinates
        if (!timesheet.getUserId().equals(requesterId)) {
            // If not own timesheet, check if user can access via org scope
            if (!orgScopeService.canAccessUser(requesterId, requesterRole, timesheet.getUserId(), companyId)) {
                throw new IllegalArgumentException("You do not have permission to view this timesheet");
            }
        }
        
        // Recalculate totals to ensure fresh data
        updateTimesheetTotals(timesheet);
        timesheetRepository.save(timesheet);
        
        return toResponse(timesheet);
    }

    /**
     * Create a new timesheet for a week.
     */
    private Timesheet createTimesheetForWeek(UUID userId, LocalDate monday, UUID companyId) {
        Timesheet timesheet = Timesheet.builder()
                .companyId(companyId)
                .userId(userId)
                .weekStart(monday)
                .status("DRAFT")
                .totalMinutes(0)
                .billableMinutes(0)
                .nonBillableMinutes(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        
        return timesheetRepository.save(timesheet);
    }

    /**
     * Update timesheet totals from time_entries.
     */
    private void updateTimesheetTotals(Timesheet timesheet) {
        LocalDate weekEnd = timesheet.getWeekStart().plusDays(6);
        
        // Get all time entries for this week
        List<TimeEntry> entries = timeEntryRepository.findByUserIdAndDateRange(
                timesheet.getUserId(),
                timesheet.getWeekStart(),
                weekEnd
        );
        
        System.out.println("=== TIMESHEET CALCULATION DEBUG ===");
        System.out.println("User ID: " + timesheet.getUserId());
        System.out.println("Week Start: " + timesheet.getWeekStart());
        System.out.println("Week End: " + weekEnd);
        System.out.println("Found " + entries.size() + " time entries");
        
        int totalMinutes = 0;
        int billableMinutes = 0;
        
        for (TimeEntry entry : entries) {
            int minutes = calculateEntryMinutes(entry);
            System.out.println("Entry: date=" + entry.getDate() + ", hours=" + entry.getHours() + ", minutes=" + minutes + ", billable=" + entry.getIsBillable());
            totalMinutes += minutes;
            if (Boolean.TRUE.equals(entry.getIsBillable())) {
                billableMinutes += minutes;
            }
        }
        
        System.out.println("Total Minutes: " + totalMinutes);
        System.out.println("Billable Minutes: " + billableMinutes);
        System.out.println("===================================");
        
        timesheet.setTotalMinutes(totalMinutes);
        timesheet.setBillableMinutes(billableMinutes);
        timesheet.setNonBillableMinutes(totalMinutes - billableMinutes);
    }

    /**
     * Calculate minutes from a time entry.
     */
    private int calculateEntryMinutes(TimeEntry entry) {
        if (entry.getHours() != null && entry.getHours() > 0) {
            return entry.getHours() * 60;
        }
        return 0;
    }

    /**
     * Convert Timesheet entity to response DTO.
     */
    private TimesheetResponse toResponse(Timesheet timesheet) {
        String userName = userRepository.findById(timesheet.getUserId())
                .map(User::getName)
                .orElse(null);
        
        // Calculate daily breakdown
        List<TimesheetResponse.DailyBreakdown> dailyBreakdown = calculateDailyBreakdown(timesheet);
        
        String approvedByName = null;
        if (timesheet.getApprovedBy() != null) {
            approvedByName = userRepository.findById(timesheet.getApprovedBy())
                    .map(User::getName)
                    .orElse(null);
        }
        
        return TimesheetResponse.builder()
                .id(timesheet.getId())
                .userId(timesheet.getUserId())
                .userName(userName)
                .weekStart(timesheet.getWeekStart())
                .status(timesheet.getStatus())
                .submittedAt(timesheet.getSubmittedAt())
                .approvedAt(timesheet.getApprovedAt())
                .approvedBy(timesheet.getApprovedBy())
                .approvedByName(approvedByName)
                .rejectionReason(timesheet.getRejectionReason())
                .totalMinutes(timesheet.getTotalMinutes())
                .billableMinutes(timesheet.getBillableMinutes())
                .nonBillableMinutes(timesheet.getNonBillableMinutes())
                .dailyBreakdown(dailyBreakdown)
                .createdAt(timesheet.getCreatedAt())
                .updatedAt(timesheet.getUpdatedAt())
                .build();
    }
    
    /**
     * Calculate daily breakdown from time entries for the week.
     */
    private List<TimesheetResponse.DailyBreakdown> calculateDailyBreakdown(Timesheet timesheet) {
        LocalDate weekEnd = timesheet.getWeekStart().plusDays(6);
        
        // Get all time entries for this week
        List<TimeEntry> entries = timeEntryRepository.findByUserIdAndDateRange(
                timesheet.getUserId(),
                timesheet.getWeekStart(),
                weekEnd
        );
        
        // Group entries by date
        Map<LocalDate, List<TimeEntry>> entriesByDate = new HashMap<>();
        for (TimeEntry entry : entries) {
            entriesByDate.computeIfAbsent(entry.getDate(), k -> new ArrayList<>()).add(entry);
        }
        
        // Create breakdown for each day of the week
        List<TimesheetResponse.DailyBreakdown> breakdown = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate date = timesheet.getWeekStart().plusDays(i);
            List<TimeEntry> dayEntries = entriesByDate.getOrDefault(date, List.of());
            
            int totalMinutes = 0;
            int billableMinutes = 0;
            
            for (TimeEntry entry : dayEntries) {
                int minutes = calculateEntryMinutes(entry);
                totalMinutes += minutes;
                if (Boolean.TRUE.equals(entry.getIsBillable())) {
                    billableMinutes += minutes;
                }
            }
            
            breakdown.add(TimesheetResponse.DailyBreakdown.builder()
                    .date(date)
                    .totalMinutes(totalMinutes)
                    .billableMinutes(billableMinutes)
                    .nonBillableMinutes(totalMinutes - billableMinutes)
                    .build());
        }
        
        return breakdown;
    }
}

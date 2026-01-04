package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimesheetResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private LocalDate weekStart;
    private String status;
    private LocalDateTime submittedAt;
    private LocalDateTime approvedAt;
    private UUID approvedBy;
    private String approvedByName;
    private String rejectionReason;
    private Integer totalMinutes;
    private Integer billableMinutes;
    private Integer nonBillableMinutes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Daily breakdown by date
    private List<DailyBreakdown> dailyBreakdown;
    
    // Optional: breakdown by project
    private List<ProjectBreakdown> breakdownByProject;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyBreakdown {
        private LocalDate date;
        private Integer totalMinutes;
        private Integer billableMinutes;
        private Integer nonBillableMinutes;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectBreakdown {
        private UUID projectId;
        private String projectName;
        private Integer minutes;
        private Integer billableMinutes;
    }
}

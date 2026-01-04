package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    // KPI Stats
    private Integer activeProjects;
    private Integer totalProjects;
    private Double hoursThisWeek;
    private Double hoursLastWeek;
    private Integer pendingTimesheets;
    private Integer teamMembers;
    private Integer activeTeamMembers;
    
    // Project breakdown
    private Integer planningProjects;
    private Integer onHoldProjects;
    private Integer completedProjects;
    private Integer cancelledProjects;
    
    // Task stats
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer inProgressTasks;
    private Integer todoTasks;
    
    // Time tracking
    private Double totalHoursLogged;
    private Double billableHours;
    private Double nonBillableHours;
    
    // Recent activities
    private List<ActivityItem> recentActivities;
    
    // Upcoming deadlines
    private List<DeadlineItem> upcomingDeadlines;
    
    // Top projects by hours
    private List<ProjectHoursItem> topProjectsByHours;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityItem {
        private String id;
        private String type; // PROJECT, TASK, TIMESHEET, CLIENT, TEAM
        private String title;
        private String description;
        private String time;
        private String userName;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeadlineItem {
        private String id;
        private String type; // PROJECT, TASK
        private String name;
        private String dueDate;
        private Integer daysRemaining;
        private String status;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectHoursItem {
        private String id;
        private String name;
        private Double hours;
        private Double billableHours;
        private String status;
    }
}

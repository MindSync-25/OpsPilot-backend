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
public class TeamReportDTO {
    private Integer totalMembers;
    private Integer activeMembers;
    private Integer totalTasksAssigned;
    private Integer totalTasksCompleted;
    private Integer totalHoursLogged;
    private Double avgProductivityScore;
    private List<MemberStats> memberStats;
    private List<TeamWorkload> workloadDistribution;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberStats {
        private String userId;
        private String userName;
        private String email;
        private Integer tasksAssigned;
        private Integer tasksCompleted;
        private Integer hoursLogged;
        private Double completionRate;
        private Double productivityScore;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TeamWorkload {
        private String userId;
        private String userName;
        private Integer currentWorkload;
        private String status; // Overloaded, Balanced, Underutilized
    }
}

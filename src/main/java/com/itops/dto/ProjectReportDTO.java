package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectReportDTO {
    private Integer totalProjects;
    private Integer activeProjects;
    private Integer completedProjects;
    private Integer onHoldProjects;
    private Integer totalTasks;
    private Integer completedTasks;
    private Integer inProgressTasks;
    private Integer todoTasks;
    private Double avgCompletionRate;
    private List<ProjectStats> projectStats;
    private List<TaskStatusDistribution> taskDistribution;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectStats {
        private String projectId;
        private String projectName;
        private String status;
        private Integer totalTasks;
        private Integer completedTasks;
        private Double completionRate;
        private Integer totalHours;
        private BigDecimal revenue;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskStatusDistribution {
        private String status;
        private Integer count;
        private Double percentage;
    }
}

package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeReportDTO {
    private Integer totalMinutes;
    private Integer billableMinutes;
    private Integer nonBillableMinutes;
    private Integer totalEntries;
    private Double avgHoursPerDay;
    private List<DailyTime> dailyTime;
    private List<UserTime> timeByUser;
    private List<ProjectTime> timeByProject;
    private List<TaskTime> timeByTask;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTime {
        private LocalDate date;
        private Integer minutes;
        private Integer billableMinutes;
        private Integer entryCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserTime {
        private String userId;
        private String userName;
        private Integer totalMinutes;
        private Integer billableMinutes;
        private Integer entryCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectTime {
        private String projectId;
        private String projectName;
        private Integer totalMinutes;
        private Integer billableMinutes;
        private Integer entryCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TaskTime {
        private String taskId;
        private String taskTitle;
        private Integer totalMinutes;
        private Integer entryCount;
    }
}

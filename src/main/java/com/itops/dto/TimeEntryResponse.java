package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimeEntryResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private UUID projectId;
    private String projectName;
    private UUID taskId;
    private String taskName;
    private String date;
    private Integer hours;
    private String description;
    private Boolean isBillable;
    private LocalDateTime createdAt;
    
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean isActive;
    private Integer durationMinutes;
}

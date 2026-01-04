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
public class ProjectResponse {
    private UUID id;
    private String name;
    private String description;
    private UUID clientId;
    private String status;
    private String priority;
    private String startDate;
    private String endDate;
    private UUID projectOwnerId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Overview statistics
    private Integer taskCount;
    private Integer completedTaskCount;
    private Integer progressPercent;
    private Double totalHours;
    private Double billableHours;
    private Integer teamMemberCount;
    private Integer phaseCount;
    private Integer activePhaseCount;
}

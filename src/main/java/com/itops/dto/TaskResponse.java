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
public class TaskResponse {
    private UUID id;
    private String title;
    private String description;
    private UUID projectId;
    private UUID phaseId; // NEW: Phase relationship
    private UUID teamId;
    private UUID assignedTo;
    private String status;
    private String priority;
    private String dueDate;
    private Integer estimatedHours;
    private String storyPoints;
    private UUID createdBy;
    private LocalDateTime createdAt;
}

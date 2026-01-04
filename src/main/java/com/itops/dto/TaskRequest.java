package com.itops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TaskRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotNull(message = "Project ID is required")
    private UUID projectId;

    private UUID phaseId;

    private UUID teamId;

    private UUID assignedTo;

    private String status = "TODO";

    private String priority = "MEDIUM";

    private String dueDate;

    private Integer estimatedHours;

    private String storyPoints;

    private UUID createdBy;
}

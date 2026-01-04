package com.itops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateSubtaskRequest {

    @NotBlank(message = "Subtask title is required")
    private String title;

    private String description;

    private String status = "TODO"; // TODO, IN_PROGRESS, DONE

    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    private UUID assignedTo;

    private LocalDate dueDate;

    private Integer sortOrder = 0;

    private String storyPoints;

    private UUID createdBy;

    private UUID parentSubtaskId;
}

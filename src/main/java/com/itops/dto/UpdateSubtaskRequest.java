package com.itops.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateSubtaskRequest {

    private String title;

    private String description;

    private String status; // TODO, IN_PROGRESS, DONE

    private String priority; // LOW, MEDIUM, HIGH, URGENT

    private UUID assignedTo;

    private LocalDate dueDate;

    private Integer sortOrder;

    private String storyPoints;
}

package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtaskResponse {

    private UUID id;
    private UUID companyId;
    private UUID taskId;
    private String title;
    private String description;
    private String status;
    private String priority;
    private UUID assignedTo;
    private LocalDate dueDate;
    private Integer sortOrder;
    private String storyPoints;
    private UUID createdBy;
    private UUID parentSubtaskId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

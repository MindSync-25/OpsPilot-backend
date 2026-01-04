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
public class PhaseResponse {
    
    private UUID id;
    private UUID companyId;
    private UUID projectId;
    private String name;
    private String description;
    private Integer sortOrder;
    private String status;
    private UUID teamId;
    private String teamName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long taskCount; // Number of tasks in this phase
}

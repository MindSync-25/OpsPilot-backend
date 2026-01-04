package com.itops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class StartTimerRequest {
    @NotNull(message = "Project ID is required")
    private UUID projectId;
    
    private UUID taskId;
    
    private Boolean isBillable = true;
    
    private String notes;
}

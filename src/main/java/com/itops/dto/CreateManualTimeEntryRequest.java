package com.itops.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class CreateManualTimeEntryRequest {
    
    @NotNull(message = "Project ID is required")
    private UUID projectId;
    
    private UUID taskId;
    
    @NotNull(message = "Date is required")
    private LocalDate date;
    
    @NotNull(message = "Hours is required")
    @Min(value = 0, message = "Hours must be positive")
    private Integer hours;
    
    private Boolean isBillable = true;
    
    private String notes;
}

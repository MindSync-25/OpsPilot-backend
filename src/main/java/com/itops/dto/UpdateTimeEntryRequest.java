package com.itops.dto;

import jakarta.validation.constraints.Min;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class UpdateTimeEntryRequest {
    
    private UUID projectId;
    
    private UUID taskId;
    
    private LocalDate date;
    
    @Min(value = 0, message = "Hours must be positive")
    private Integer hours;
    
    private Boolean isBillable;
    
    private String notes;
}

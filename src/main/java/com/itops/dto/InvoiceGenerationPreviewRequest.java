package com.itops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class InvoiceGenerationPreviewRequest {
    
    @NotNull(message = "Client ID is required")
    private UUID clientId;
    
    private UUID projectId; // Optional - filter to specific project
    
    @NotNull(message = "From date is required")
    private LocalDate fromDate;
    
    @NotNull(message = "To date is required")
    private LocalDate toDate;
    
    private Boolean billableOnly = true; // Default to billable only
    
    private GroupBy groupBy = GroupBy.USER; // Default grouping
    
    private Boolean includeDescriptions = false; // Include entry descriptions
    
    public enum GroupBy {
        USER,  // Group by user (recommended)
        TASK   // Group by task
    }
}

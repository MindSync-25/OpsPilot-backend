package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PreviewLineItem {
    
    private String description;
    
    private Integer quantityMinutes; // Total minutes for this line
    
    private BigDecimal quantityHours; // Decimal hours (for display)
    
    private BigDecimal unitPrice; // Hourly rate
    
    private BigDecimal amount; // Total for this line
    
    private UUID userId; // Present if groupBy = USER
    
    private String userName; // For display
    
    private UUID taskId; // Present if groupBy = TASK
    
    private String taskTitle; // For display
    
    private String aggregatedNotes; // If includeDescriptions = true
}

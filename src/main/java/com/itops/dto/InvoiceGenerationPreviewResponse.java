package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceGenerationPreviewResponse {
    
    private UUID clientId;
    
    private String clientName;
    
    private UUID projectId;
    
    private String projectName;
    
    private LocalDate fromDate;
    
    private LocalDate toDate;
    
    private Integer totalMinutes;
    
    private BigDecimal totalHours; // Decimal hours for display
    
    private BigDecimal subtotal;
    
    private BigDecimal taxRate;
    
    private BigDecimal taxAmount;
    
    private BigDecimal total;
    
    @Builder.Default
    private List<PreviewLineItem> lineItems = new ArrayList<>();
    
    @Builder.Default
    private List<MissingRateUser> missingRateUsers = new ArrayList<>();
    
    private Integer entriesCount; // Number of time entries included
    
    private Boolean canGenerate; // False if missing rates or no entries
    
    private String message; // Explanation if can't generate
}

package com.itops.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class CreateInvoiceRequest {
    
    @NotNull(message = "Client ID is required")
    private UUID clientId;
    
    private UUID projectId; // Optional
    
    @NotNull(message = "Issue date is required")
    private LocalDate issueDate;
    
    private LocalDate dueDate; // Optional
    
    private BigDecimal taxRate; // Optional, defaults to 18.00
    
    private String notes;
    
    @NotEmpty(message = "At least one invoice item is required")
    @Valid
    private List<InvoiceItemRequest> items;
}

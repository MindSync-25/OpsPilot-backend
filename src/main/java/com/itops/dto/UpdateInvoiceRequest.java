package com.itops.dto;

import jakarta.validation.Valid;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class UpdateInvoiceRequest {
    
    private UUID clientId;
    private UUID projectId;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private BigDecimal taxRate;
    private String notes;
    
    @Valid
    private List<InvoiceItemRequest> items; // Full replacement
}

package com.itops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class InvoiceGenerateRequest extends InvoiceGenerationPreviewRequest {
    
    private BigDecimal taxRate; // Optional override (default 18.00)
    
    private String notes; // Invoice notes/terms
    
    @NotNull(message = "Confirmation required")
    private Boolean confirmed = false; // Must be true to generate
}

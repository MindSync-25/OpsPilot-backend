package com.itops.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateInvoiceStatusRequest {
    
    @NotBlank(message = "Status is required")
    @Pattern(regexp = "DRAFT|SENT|PAID|OVERDUE|CANCELLED", 
             message = "Status must be one of: DRAFT, SENT, PAID, OVERDUE, CANCELLED")
    private String status;
}

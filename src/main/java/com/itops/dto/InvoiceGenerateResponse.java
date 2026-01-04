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
public class InvoiceGenerateResponse {
    
    private UUID invoiceId;
    
    private String invoiceNumber;
    
    private BigDecimal total;
    
    private Integer billedEntriesCount;
    
    private String message;
}

package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceResponse {
    private UUID id;
    private UUID companyId;
    private UUID clientId;
    private UUID projectId;
    private String invoiceNumber;
    private LocalDate issueDate;
    private LocalDate dueDate;
    private String status;
    private BigDecimal subtotal;
    private BigDecimal taxRate;
    private BigDecimal taxAmount;
    private BigDecimal total;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Boolean isOverdue;
    private List<InvoiceItemResponse> items;
    
    // Enhanced fields
    private InvoiceClientInfo client;
    private InvoiceProjectInfo project;
    private InvoiceUserInfo createdBy;
    private LocalDate billingPeriodStart;
    private LocalDate billingPeriodEnd;
    private String currencyCode;
    private String paymentTerms;
    private LocalDateTime sentAt;
    private LocalDateTime paidAt;
    private LocalDateTime cancelledAt;
    private InvoiceSummary summary;
}
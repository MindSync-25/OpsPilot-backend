package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoices")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Invoice extends BaseEntity {
    
    @Column(name = "invoice_number", nullable = false, unique = true)
    private String invoiceNumber;
    
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    
    @Column(name = "project_id")
    private UUID projectId;
    
    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;
    
    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;
    
    @Column(nullable = false)
    private String status; // DRAFT, SENT, PAID, OVERDUE, CANCELLED
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;
    
    @Column(name = "tax_rate", precision = 5, scale = 2)
    private BigDecimal taxRate;
    
    @Column(name = "tax_amount", precision = 10, scale = 2)
    private BigDecimal taxAmount;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal total;
    
    @Column(columnDefinition = "TEXT")
    private String notes;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "billing_period_start")
    private LocalDate billingPeriodStart;
    
    @Column(name = "billing_period_end")
    private LocalDate billingPeriodEnd;
    
    @Column(name = "currency_code", nullable = false, length = 10)
    private String currencyCode = "USD";
    
    @Column(name = "payment_terms", length = 50)
    private String paymentTerms;
    
    @Column(name = "sent_at")
    private java.time.LocalDateTime sentAt;
    
    @Column(name = "paid_at")
    private java.time.LocalDateTime paidAt;
    
    @Column(name = "cancelled_at")
    private java.time.LocalDateTime cancelledAt;
}

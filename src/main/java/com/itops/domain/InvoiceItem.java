package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "invoice_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceItem extends BaseEntity {
    
    @Column(name = "invoice_id", nullable = false)
    private UUID invoiceId;
    
    @Column(nullable = false)
    private String description;
    
    @Column(nullable = false)
    private Integer quantity;
    
    @Column(name = "unit_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal unitPrice;
    
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;
    
    @Column(name = "item_type", nullable = false, length = 50)
    private String itemType = "MANUAL";
    
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "task_id")
    private UUID taskId;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal rate;
    
    @Column
    private Integer minutes;
    
    @Column(name = "source_time_entry_ids", columnDefinition = "TEXT")
    private String sourceTimeEntryIds;
}

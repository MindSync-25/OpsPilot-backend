package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "time_entries")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TimeEntry extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private Integer hours;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_billable", nullable = false)
    private Boolean isBillable = true;
    
    @Column(name = "start_time")
    private LocalDateTime startTime;
    
    @Column(name = "end_time")
    private LocalDateTime endTime;
    
    @Column(name = "is_active")
    private Boolean isActive = false;
    
    @Column(name = "invoice_id")
    private UUID invoiceId;
    
    @Column(name = "billed_at")
    private LocalDateTime billedAt;
}

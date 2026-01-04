package com.itops.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "timesheets",
        uniqueConstraints = @UniqueConstraint(
                name = "unique_timesheet_per_week",
                columnNames = {"company_id", "user_id", "week_start"}
        ))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Timesheet {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "week_start", nullable = false)
    private LocalDate weekStart; // Monday of the week

    @Column(length = 30, nullable = false)
    private String status = "DRAFT"; // DRAFT, SUBMITTED, APPROVED, REJECTED

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Column(name = "total_minutes", nullable = false)
    private Integer totalMinutes = 0;

    @Column(name = "billable_minutes", nullable = false)
    private Integer billableMinutes = 0;

    @Column(name = "non_billable_minutes", nullable = false)
    private Integer nonBillableMinutes = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}

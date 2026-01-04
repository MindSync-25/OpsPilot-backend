package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Project extends BaseEntity {
    
    @Column(nullable = false)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "client_id", nullable = false)
    private UUID clientId;
    
    @Column(nullable = false)
    private String status; // PLANNING, ACTIVE, ON_HOLD, COMPLETED, CANCELLED
    
    @Column(name = "start_date")
    private LocalDate startDate;
    
    @Column(name = "end_date")
    private LocalDate endDate;
    
    @Column(nullable = false)
    private String priority; // LOW, MEDIUM, HIGH
    
    @Column(name = "project_owner_id")
    private UUID projectOwnerId;
}

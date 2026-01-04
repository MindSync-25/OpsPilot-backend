package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "phase_id")
    private UUID phaseId;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(nullable = false)
    private String status;

    private String priority;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "estimated_hours")
    private Integer estimatedHours;

    @Column(name = "story_points")
    private String storyPoints;

    @Column(name = "created_by")
    private UUID createdBy;
}

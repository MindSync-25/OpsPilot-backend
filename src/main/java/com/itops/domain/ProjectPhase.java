package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "project_phases")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectPhase extends BaseEntity {

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(nullable = false, length = 50)
    private String status = "TODO"; // TODO, ACTIVE, COMPLETED, ARCHIVED

    @Column(name = "team_id")
    private UUID teamId;

    // Relationships can be added later if needed
    // @OneToMany(mappedBy = "phaseId")
    // private List<Task> tasks;
}

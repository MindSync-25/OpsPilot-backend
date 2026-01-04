package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "subtasks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subtask extends BaseEntity {

    @Column(name = "task_id", nullable = false)
    private UUID taskId;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 50)
    private String status = "TODO"; // TODO, IN_PROGRESS, DONE

    @Column(length = 50)
    private String priority = "MEDIUM"; // LOW, MEDIUM, HIGH, URGENT

    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "story_points")
    private String storyPoints;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "parent_subtask_id")
    private UUID parentSubtaskId;
}

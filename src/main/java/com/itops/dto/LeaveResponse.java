package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaveResponse {
    private UUID id;
    private UUID userId;
    private String userName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String type;
    private String status;
    private String reason;
    private UUID approverId;
    private String approverName;
    private String decisionNote;
    private LocalDateTime decidedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

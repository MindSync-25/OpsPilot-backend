package com.itops.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class ReportFilterDTO {
    private String period; // 7days, month, quarter, 6months, year, overall
    private LocalDate startDate;
    private LocalDate endDate;
    private UUID clientId;
    private UUID projectId;
    private UUID teamId;
    private UUID userId;
}

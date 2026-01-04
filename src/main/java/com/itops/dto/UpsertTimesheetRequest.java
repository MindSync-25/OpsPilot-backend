package com.itops.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpsertTimesheetRequest {
    @NotNull(message = "Week start date is required")
    private LocalDate weekStart;
    
    private String note;
}

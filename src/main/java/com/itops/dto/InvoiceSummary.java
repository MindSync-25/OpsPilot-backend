package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceSummary {
    private Integer totalMinutes;
    private Integer totalBillableMinutes;
    private Integer entryCount;
    private Integer contributorsCount;
    private Integer tasksCount;
}

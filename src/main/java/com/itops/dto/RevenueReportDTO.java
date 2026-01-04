package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportDTO {
    private BigDecimal totalRevenue;
    private BigDecimal paidAmount;
    private BigDecimal pendingAmount;
    private BigDecimal overdueAmount;
    private Integer totalInvoices;
    private Integer paidInvoices;
    private Integer pendingInvoices;
    private Integer overdueInvoices;
    private List<DailyRevenue> dailyRevenue;
    private List<ClientRevenue> revenueByClient;
    private List<ProjectRevenue> revenueByProject;
    private Map<String, BigDecimal> revenueByStatus;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyRevenue {
        private LocalDate date;
        private BigDecimal amount;
        private Integer invoiceCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientRevenue {
        private String clientId;
        private String clientName;
        private BigDecimal revenue;
        private Integer invoiceCount;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProjectRevenue {
        private String projectId;
        private String projectName;
        private BigDecimal revenue;
        private Integer invoiceCount;
    }
}

package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsResponse {

    private RevenueAnalytics revenueAnalytics;
    private List<CohortData> cohortAnalysis;
    private List<PerformanceMetric> performanceMetrics;
    private List<ResourceUtilization> resourceUtilization;
    private List<ProfitabilityData> profitabilityTrend;
    private List<ClientSegmentValue> clientLifetimeValue;
    private List<KpiMetric> kpiMetrics;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RevenueAnalytics {
        private List<MonthlyRevenue> forecast;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyRevenue {
        private String month;
        private BigDecimal actual;
        private BigDecimal predicted;
        private BigDecimal upper;
        private BigDecimal lower;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CohortData {
        private String cohort;
        private Integer m1;
        private Integer m2;
        private Integer m3;
        private Integer m4;
        private Integer m5;
        private Integer m6;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PerformanceMetric {
        private String metric;
        private Integer score;
        private Integer target;
        private Integer max;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResourceUtilization {
        private String name;
        private Integer planned;
        private Integer actual;
        private Integer billable;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfitabilityData {
        private String month;
        private BigDecimal revenue;
        private BigDecimal cost;
        private BigDecimal profit;
        private BigDecimal margin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientSegmentValue {
        private String segment;
        private BigDecimal ltv;
        private BigDecimal acquisition;
        private Integer retention;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KpiMetric {
        private String title;
        private String value;
        private String confidence;
        private String trend;
        private String change;
        private String icon;
        private String color;
    }
}

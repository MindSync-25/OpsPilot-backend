package com.itops.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanResponse {
    private UUID id;
    private String code;
    private String name;
    private BigDecimal priceMonthly;
    private BigDecimal priceYearly;
    private String currencyCode;
    private Integer maxUsers;
    private Integer maxProjects;
    private Map<String, Object> featureFlags;
    private Boolean isActive;
}

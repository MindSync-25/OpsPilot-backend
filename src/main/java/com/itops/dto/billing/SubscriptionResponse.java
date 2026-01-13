package com.itops.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubscriptionResponse {
    private UUID id;
    private UUID companyId;
    private String planCode;
    private String planName;
    private PlanResponse plan;  // Full plan with feature flags
    private String status;
    private String billingCycle;
    private LocalDateTime currentPeriodStart;
    private LocalDateTime currentPeriodEnd;
    private Boolean cancelAtPeriodEnd;
    
    // Plan limits
    private Integer maxUsers;
    private Integer maxProjects;
    
    // Current usage
    private Integer currentUsers;
    private Integer currentProjects;
    
    // Razorpay info (minimal)
    private String rzSubscriptionId;
}

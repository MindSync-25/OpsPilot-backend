package com.itops.service;

import com.itops.domain.Plan;
import com.itops.dto.billing.PlanResponse;
import com.itops.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanService {
    
    private final PlanRepository planRepository;
    
    @Transactional(readOnly = true)
    public List<PlanResponse> listPlans() {
        return planRepository.findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrder()
            .stream()
            .map(this::toPlanResponse)
            .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public Plan getPlanByCode(String code) {
        return planRepository.findByCode(code)
            .orElseThrow(() -> new RuntimeException("Plan not found with code: " + code));
    }
    
    private PlanResponse toPlanResponse(Plan plan) {
        return PlanResponse.builder()
            .id(plan.getId())
            .code(plan.getCode())
            .name(plan.getName())
            .priceMonthly(plan.getPriceMonthly())
            .priceYearly(plan.getPriceYearly())
            .currencyCode(plan.getCurrencyCode())
            .maxUsers(plan.getMaxUsers())
            .maxProjects(plan.getMaxProjects())
            .featureFlags(plan.getFeatureFlags())
            .isActive(plan.getIsActive())
            .build();
    }
}

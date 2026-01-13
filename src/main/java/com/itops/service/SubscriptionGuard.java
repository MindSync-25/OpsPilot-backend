package com.itops.service;

import com.itops.domain.Plan;
import com.itops.domain.Subscription;
import com.itops.domain.Subscription.SubscriptionStatus;
import com.itops.exception.SubscriptionLimitException;
import com.itops.exception.SubscriptionRequiredException;
import com.itops.repository.PlanRepository;
import com.itops.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionGuard {
    
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final UsageService usageService;
    
    @Transactional(readOnly = true)
    public void enforceUserCreation(UUID companyId) {
        Subscription subscription = getActiveSubscription(companyId);
        Plan plan = getPlan(subscription.getPlanCode());
        
        int currentUsers = usageService.countActiveUsers(companyId);
        
        if (currentUsers >= plan.getMaxUsers()) {
            throw new SubscriptionLimitException(
                "User limit reached. Current plan allows " + plan.getMaxUsers() + " users. Upgrade required."
            );
        }
    }
    
    @Transactional(readOnly = true)
    public void enforceProjectCreation(UUID companyId) {
        Subscription subscription = getActiveSubscription(companyId);
        Plan plan = getPlan(subscription.getPlanCode());
        
        int currentProjects = usageService.countActiveProjects(companyId);
        
        if (currentProjects >= plan.getMaxProjects()) {
            throw new SubscriptionLimitException(
                "Project limit reached. Current plan allows " + plan.getMaxProjects() + " projects. Upgrade required."
            );
        }
    }
    
    private Subscription getActiveSubscription(UUID companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
            .orElseThrow(() -> new SubscriptionRequiredException("No subscription found. Please subscribe to a plan."));
        
        if (subscription.getStatus() != SubscriptionStatus.ACTIVE && 
            subscription.getStatus() != SubscriptionStatus.TRIALING) {
            throw new SubscriptionRequiredException(
                "Subscription is not active. Current status: " + subscription.getStatus() + ". Please update your payment method."
            );
        }
        
        return subscription;
    }
    
    private Plan getPlan(String planCode) {
        return planRepository.findByCode(planCode)
            .orElseThrow(() -> new RuntimeException("Plan not found: " + planCode));
    }
}

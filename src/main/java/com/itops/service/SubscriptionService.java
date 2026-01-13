package com.itops.service;

import com.itops.config.RazorpayConfig;
import com.itops.domain.*;
import com.itops.domain.Subscription.BillingCycle;
import com.itops.domain.Subscription.SubscriptionStatus;
import com.itops.dto.billing.CheckoutResponse;
import com.itops.dto.billing.PlanResponse;
import com.itops.dto.billing.SubscriptionResponse;
import com.itops.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    private final CompanyRepository companyRepository;
    private final UserRepository userRepository;
    private final BillingEventRepository billingEventRepository;
    private final RazorpayClient razorpayClient;
    private final RazorpayConfig razorpayConfig;
    private final UsageService usageService;
    
    @Transactional
    public SubscriptionResponse getCompanySubscription(UUID companyId) {
        Subscription subscription = subscriptionRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
            .orElseGet(() -> {
                // Auto-create trial subscription for existing companies without one
                log.info("Creating trial subscription for company: {}", companyId);
                return createOrUpdateTrial(companyId, "STARTER");
            });
        
        Plan plan = planRepository.findByCode(subscription.getPlanCode())
            .orElseThrow(() -> new RuntimeException("Plan not found: " + subscription.getPlanCode()));
        
        int currentUsers = usageService.countActiveUsers(companyId);
        int currentProjects = usageService.countActiveProjects(companyId);
        
        PlanResponse planResponse = PlanResponse.builder()
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
        
        return SubscriptionResponse.builder()
            .id(subscription.getId())
            .companyId(subscription.getCompanyId())
            .planCode(subscription.getPlanCode())
            .planName(plan.getName())
            .plan(planResponse)
            .status(subscription.getStatus().name())
            .billingCycle(subscription.getBillingCycle().name())
            .currentPeriodStart(subscription.getCurrentPeriodStart())
            .currentPeriodEnd(subscription.getCurrentPeriodEnd())
            .cancelAtPeriodEnd(subscription.getCancelAtPeriodEnd())
            .maxUsers(plan.getMaxUsers())
            .maxProjects(plan.getMaxProjects())
            .currentUsers(currentUsers)
            .currentProjects(currentProjects)
            .rzSubscriptionId(subscription.getRzSubscriptionId())
            .build();
    }
    
    @Transactional
    public Subscription createOrUpdateTrial(UUID companyId, String planCode) {
        Optional<Subscription> existing = subscriptionRepository.findByCompanyIdAndDeletedAtIsNull(companyId);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Validate plan exists
        planRepository.findByCode(planCode)
            .orElseThrow(() -> new RuntimeException("Invalid plan code: " + planCode));
        
        // Create trial subscription on selected plan (14 days free trial)
        Subscription subscription = Subscription.builder()
            .companyId(companyId)
            .planCode(planCode)
            .status(SubscriptionStatus.TRIALING)
            .billingCycle(BillingCycle.MONTHLY)
            .currentPeriodStart(LocalDateTime.now())
            .currentPeriodEnd(LocalDateTime.now().plusDays(14)) // 14 day trial
            .cancelAtPeriodEnd(false)
            .build();
        
        return subscriptionRepository.save(subscription);
    }
    
    @Transactional
    public CheckoutResponse createCheckout(UUID companyId, String planCode, String billingCycle, 
                                          String userEmail, String userName) {
        // Validate plan
        Plan plan = planRepository.findByCode(planCode)
            .orElseThrow(() -> new RuntimeException("Invalid plan code: " + planCode));
        
        if (!plan.getIsActive()) {
            throw new RuntimeException("Plan is not active: " + planCode);
        }
        
        // Get company
        Company company = companyRepository.findById(companyId)
            .orElseThrow(() -> new RuntimeException("Company not found"));
        
        // Get or create Razorpay customer
        String customerId;
        Subscription existingSubscription = subscriptionRepository.findByCompanyIdAndDeletedAtIsNull(companyId)
            .orElse(null);
        
        if (existingSubscription != null && existingSubscription.getRzCustomerId() != null) {
            customerId = existingSubscription.getRzCustomerId();
        } else {
            Map<String, Object> customer = razorpayClient.createCustomer(userEmail, userName, company.getName());
            customerId = (String) customer.get("id");
        }
        
        // Determine price based on billing cycle
        BigDecimal amount = billingCycle.equals("YEARLY") ? plan.getPriceYearly() : plan.getPriceMonthly();
        
        // For Razorpay, you need to create a plan on Razorpay dashboard first
        // For this implementation, we'll use a naming convention: planCode_billingCycle
        String rzPlanId = "plan_" + planCode.toLowerCase() + "_" + billingCycle.toLowerCase();
        
        // Create Razorpay subscription
        Map<String, String> notes = new HashMap<>();
        notes.put("company_id", companyId.toString());
        notes.put("plan_code", planCode);
        notes.put("billing_cycle", billingCycle);
        
        Map<String, Object> rzSubscription = razorpayClient.createSubscription(
            rzPlanId, 
            customerId, 
            0, // 0 = ongoing subscription
            notes
        );
        
        String subscriptionId = (String) rzSubscription.get("id");
        
        // Update or create subscription in database
        Subscription subscription;
        if (existingSubscription != null) {
            subscription = existingSubscription;
        } else {
            subscription = new Subscription();
            subscription.setCompanyId(companyId);
        }
        
        subscription.setPlanCode(planCode);
        subscription.setStatus(SubscriptionStatus.TRIALING); // Will be updated by webhook
        subscription.setBillingCycle(BillingCycle.valueOf(billingCycle));
        subscription.setRzCustomerId(customerId);
        subscription.setRzSubscriptionId(subscriptionId);
        subscription.setRzPlanId(rzPlanId);
        subscription.setCancelAtPeriodEnd(false);
        
        subscriptionRepository.save(subscription);
        
        return CheckoutResponse.builder()
            .keyId(razorpayConfig.getKeyId())
            .subscriptionId(subscriptionId)
            .customerId(customerId)
            .planCode(planCode)
            .billingCycle(billingCycle)
            .amount(amount)
            .currency(plan.getCurrencyCode())
            .companyName(company.getName())
            .userName(userName)
            .userEmail(userEmail)
            .build();
    }
    
    @Transactional
    public void handleWebhook(Map<String, Object> payload, String signature) {
        // Extract event details
        String eventId = (String) payload.get("event");
        String eventType = (String) payload.get("event");
        
        // Check idempotency
        if (billingEventRepository.existsByEventId(eventId)) {
            log.info("Event already processed: {}", eventId);
            return;
        }
        
        // Verify signature
        verifyWebhookSignature(payload, signature);
        
        // Store event
        BillingEvent billingEvent = BillingEvent.builder()
            .provider("RAZORPAY")
            .eventId(eventId)
            .eventType(eventType)
            .payload(payload)
            .receivedAt(LocalDateTime.now())
            .build();
        
        billingEventRepository.save(billingEvent);
        
        // Process event
        applyEvent(billingEvent);
    }
    
    @Transactional
    public void applyEvent(BillingEvent event) {
        String eventType = event.getEventType();
        Map<String, Object> payload = event.getPayload();
        
        // Extract subscription entity from payload
        @SuppressWarnings("unchecked")
        Map<String, Object> payloadData = (Map<String, Object>) payload.get("payload");
        if (payloadData == null) {
            log.warn("No payload data in event: {}", event.getEventId());
            return;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> subscriptionEntity = (Map<String, Object>) payloadData.get("subscription");
        if (subscriptionEntity == null && payloadData.get("payment") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentEntity = (Map<String, Object>) payloadData.get("payment");
            @SuppressWarnings("unchecked")
            Map<String, Object> paymentNotes = (Map<String, Object>) paymentEntity.get("notes");
            
            // Try to find subscription by notes
            if (paymentNotes != null && paymentNotes.get("subscription_id") != null) {
                String rzSubId = (String) paymentNotes.get("subscription_id");
                Subscription sub = subscriptionRepository.findByRzSubscriptionId(rzSubId).orElse(null);
                if (sub != null) {
                    processPaymentEvent(sub, paymentEntity, eventType);
                    event.setProcessedAt(LocalDateTime.now());
                    event.setSubscriptionId(sub.getId());
                    event.setCompanyId(sub.getCompanyId());
                    billingEventRepository.save(event);
                }
            }
            return;
        }
        
        if (subscriptionEntity == null) {
            log.warn("No subscription entity in event: {}", event.getEventId());
            return;
        }
        
        String rzSubscriptionId = (String) subscriptionEntity.get("id");
        
        // Find subscription
        Subscription subscription = subscriptionRepository.findByRzSubscriptionId(rzSubscriptionId)
            .orElse(null);
        
        if (subscription == null) {
            log.warn("Subscription not found for Razorpay ID: {}", rzSubscriptionId);
            return;
        }
        
        // Update subscription based on event type
        switch (eventType) {
            case "subscription.activated":
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                updatePeriods(subscription, subscriptionEntity);
                break;
                
            case "subscription.charged":
                subscription.setStatus(SubscriptionStatus.ACTIVE);
                updatePeriods(subscription, subscriptionEntity);
                // Store payment ID if present
                if (payloadData.get("payment") != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> payment = (Map<String, Object>) payloadData.get("payment");
                    subscription.setRzPaymentId((String) payment.get("id"));
                }
                break;
                
            case "subscription.completed":
            case "subscription.cancelled":
                subscription.setStatus(SubscriptionStatus.CANCELED);
                break;
                
            case "payment.failed":
            case "subscription.halted":
                subscription.setStatus(SubscriptionStatus.PAST_DUE);
                break;
                
            default:
                log.info("Unhandled event type: {}", eventType);
        }
        
        subscription.setLastEventId(event.getEventId());
        subscriptionRepository.save(subscription);
        
        // Update event
        event.setProcessedAt(LocalDateTime.now());
        event.setSubscriptionId(subscription.getId());
        event.setCompanyId(subscription.getCompanyId());
        billingEventRepository.save(event);
        
        log.info("Event processed: {} for subscription: {}", event.getEventId(), subscription.getId());
    }
    
    private void processPaymentEvent(Subscription subscription, Map<String, Object> paymentEntity, String eventType) {
        if ("payment.captured".equals(eventType)) {
            subscription.setRzPaymentId((String) paymentEntity.get("id"));
            subscriptionRepository.save(subscription);
        }
    }
    
    private void updatePeriods(Subscription subscription, Map<String, Object> subscriptionEntity) {
        Object currentStart = subscriptionEntity.get("current_start");
        Object currentEnd = subscriptionEntity.get("current_end");
        
        if (currentStart != null) {
            long startEpoch = getLongValue(currentStart);
            subscription.setCurrentPeriodStart(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(startEpoch), ZoneId.systemDefault())
            );
        }
        
        if (currentEnd != null) {
            long endEpoch = getLongValue(currentEnd);
            subscription.setCurrentPeriodEnd(
                LocalDateTime.ofInstant(Instant.ofEpochSecond(endEpoch), ZoneId.systemDefault())
            );
        }
    }
    
    private long getLongValue(Object value) {
        if (value instanceof Integer) {
            return ((Integer) value).longValue();
        } else if (value instanceof Long) {
            return (Long) value;
        }
        return Long.parseLong(value.toString());
    }
    
    private void verifyWebhookSignature(Map<String, Object> payload, String receivedSignature) {
        try {
            String webhookSecret = razorpayConfig.getWebhookSecret();
            
            // Convert payload to JSON string (order matters for Razorpay)
            String payloadString = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            
            Mac sha256HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256HMAC.init(secretKey);
            
            byte[] hash = sha256HMAC.doFinal(payloadString.getBytes(StandardCharsets.UTF_8));
            String calculatedSignature = bytesToHex(hash);
            
            if (!calculatedSignature.equals(receivedSignature)) {
                throw new RuntimeException("Invalid webhook signature");
            }
        } catch (Exception e) {
            log.error("Webhook signature verification failed", e);
            throw new RuntimeException("Webhook signature verification failed: " + e.getMessage());
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}

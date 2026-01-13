package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "company_id", nullable = false)
    private UUID companyId;
    
    @Column(name = "plan_code", nullable = false, length = 50)
    private String planCode;
    
    @Column(nullable = false, length = 30)
    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;
    
    @Column(name = "billing_cycle", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private BillingCycle billingCycle;
    
    @Column(name = "current_period_start")
    private LocalDateTime currentPeriodStart;
    
    @Column(name = "current_period_end")
    private LocalDateTime currentPeriodEnd;
    
    @Column(name = "cancel_at_period_end", nullable = false)
    private Boolean cancelAtPeriodEnd = false;
    
    // Razorpay fields
    @Column(name = "rz_customer_id", length = 64)
    private String rzCustomerId;
    
    @Column(name = "rz_subscription_id", length = 64)
    private String rzSubscriptionId;
    
    @Column(name = "rz_plan_id", length = 64)
    private String rzPlanId;
    
    @Column(name = "rz_payment_id", length = 64)
    private String rzPaymentId;
    
    @Column(name = "rz_order_id", length = 64)
    private String rzOrderId;
    
    @Column(name = "rz_signature", length = 256)
    private String rzSignature;
    
    @Column(name = "last_event_id", length = 128)
    private String lastEventId;
    
    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    public enum SubscriptionStatus {
        TRIALING,
        ACTIVE,
        PAST_DUE,
        CANCELED
    }
    
    public enum BillingCycle {
        MONTHLY,
        YEARLY
    }
}

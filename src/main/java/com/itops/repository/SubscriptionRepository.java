package com.itops.repository;

import com.itops.domain.Subscription;
import com.itops.domain.Subscription.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
    
    Optional<Subscription> findByCompanyIdAndDeletedAtIsNull(UUID companyId);
    
    Optional<Subscription> findByRzSubscriptionId(String rzSubscriptionId);
    
    List<Subscription> findByStatusAndDeletedAtIsNull(SubscriptionStatus status);
}

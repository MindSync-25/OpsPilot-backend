package com.itops.repository;

import com.itops.domain.BillingEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface BillingEventRepository extends JpaRepository<BillingEvent, UUID> {
    
    List<BillingEvent> findByCompanyId(UUID companyId);
    
    Optional<BillingEvent> findByEventId(String eventId);
    
    boolean existsByEventId(String eventId);
}

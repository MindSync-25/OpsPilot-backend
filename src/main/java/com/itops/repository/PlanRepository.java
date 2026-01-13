package com.itops.repository;

import com.itops.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PlanRepository extends JpaRepository<Plan, UUID> {
    
    Optional<Plan> findByCode(String code);
    
    List<Plan> findByIsActiveTrueAndDeletedAtIsNullOrderBySortOrder();
}

package com.itops.repository;

import com.itops.domain.ProjectPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectPhaseRepository extends JpaRepository<ProjectPhase, UUID> {
    
    List<ProjectPhase> findByCompanyIdAndProjectIdAndDeletedAtIsNullOrderBySortOrder(UUID companyId, UUID projectId);
    
    List<ProjectPhase> findByProjectIdAndDeletedAtIsNullOrderBySortOrder(UUID projectId);
    
    Optional<ProjectPhase> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
    
    @Query("SELECT p FROM ProjectPhase p WHERE p.companyId = ?1 AND p.deletedAt IS NULL ORDER BY p.sortOrder")
    List<ProjectPhase> findActivePhasesByCompany(UUID companyId);
    
    @Query("SELECT DISTINCT p.projectId FROM ProjectPhase p WHERE p.teamId = ?1 AND p.deletedAt IS NULL")
    List<UUID> findProjectIdsByTeamId(UUID teamId);
}

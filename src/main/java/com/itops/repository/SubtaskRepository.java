package com.itops.repository;

import com.itops.domain.Subtask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SubtaskRepository extends JpaRepository<Subtask, UUID> {
    
    List<Subtask> findByCompanyIdAndTaskIdAndDeletedAtIsNullOrderBySortOrder(UUID companyId, UUID taskId);
    
    List<Subtask> findByTaskIdAndDeletedAtIsNullOrderBySortOrder(UUID taskId);
    
    Optional<Subtask> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
}

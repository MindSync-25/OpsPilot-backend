package com.itops.repository;

import com.itops.domain.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByCompanyId(UUID companyId);
    List<Task> findByProjectId(UUID projectId);
    List<Task> findByProjectIdAndPhaseId(UUID projectId, UUID phaseId);
    List<Task> findByPhaseId(UUID phaseId);
    List<Task> findByProjectIdAndDeletedAtIsNull(UUID projectId);
}

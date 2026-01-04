package com.itops.repository;

import com.itops.domain.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID> {
    List<Project> findByCompanyId(UUID companyId);
    List<Project> findByClientIdAndCompanyId(UUID clientId, UUID companyId);
    List<Project> findByClientIdAndCompanyIdAndDeletedAtIsNull(UUID clientId, UUID companyId);
    List<Project> findByCompanyIdAndDeletedAtIsNull(UUID companyId);
}

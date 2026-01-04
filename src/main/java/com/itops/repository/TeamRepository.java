package com.itops.repository;

import com.itops.domain.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TeamRepository extends JpaRepository<Team, UUID> {
    List<Team> findByCompanyId(UUID companyId);
    List<Team> findByCreatedByUserId(UUID createdByUserId);
    List<Team> findByCreatedByUserIdAndDeletedAtIsNull(UUID createdByUserId);
}

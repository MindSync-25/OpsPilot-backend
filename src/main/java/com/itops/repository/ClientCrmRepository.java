package com.itops.repository;

import com.itops.domain.ClientCrm;
import com.itops.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientCrmRepository extends JpaRepository<ClientCrm, UUID> {
    Optional<ClientCrm> findByClientId(UUID clientId);

    @Query("SELECT c FROM ClientCrm c WHERE c.client.companyId = :companyId")
    List<ClientCrm> findAllByCompanyId(@Param("companyId") UUID companyId);

    @Query("SELECT c FROM ClientCrm c WHERE c.client.companyId = :companyId AND c.leadStage = :leadStage")
    List<ClientCrm> findAllByCompanyIdAndLeadStage(@Param("companyId") UUID companyId, @Param("leadStage") String leadStage);
}

package com.itops.repository;

import com.itops.domain.Client;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClientRepository extends JpaRepository<Client, UUID> {

    // Find all clients by company (excluding soft deleted)
    List<Client> findAllByCompanyIdAndDeletedAtIsNull(UUID companyId);

    // Find client by ID and company (excluding soft deleted)
    Optional<Client> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);

    // Find clients by company and status (excluding soft deleted)
    List<Client> findAllByCompanyIdAndStatusAndDeletedAtIsNull(UUID companyId, String status);

    // Search clients by name or email (excluding soft deleted)
    @Query("SELECT c FROM Client c WHERE c.companyId = :companyId " +
           "AND c.deletedAt IS NULL " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Client> searchClients(@Param("companyId") UUID companyId, 
                              @Param("search") String search);

    // Search clients by name, email, and status (excluding soft deleted)
    @Query("SELECT c FROM Client c WHERE c.companyId = :companyId " +
           "AND c.status = :status " +
           "AND c.deletedAt IS NULL " +
           "AND (LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    List<Client> searchClientsByStatus(@Param("companyId") UUID companyId, 
                                      @Param("status") String status,
                                      @Param("search") String search);
       // Find all business clients (lead_stage = 'WON')
       @Query("SELECT c FROM Client c JOIN ClientCrm crm ON crm.client = c WHERE c.companyId = :companyId AND c.deletedAt IS NULL AND crm.leadStage = 'WON'")
       List<Client> findAllActiveBusinessClients(@Param("companyId") UUID companyId);
}

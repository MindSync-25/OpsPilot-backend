package com.itops.repository;

import com.itops.domain.Invoice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
    List<Invoice> findAllByCompanyIdAndDeletedAtIsNull(UUID companyId);
    Optional<Invoice> findByIdAndCompanyIdAndDeletedAtIsNull(UUID id, UUID companyId);
    Optional<Invoice> findByInvoiceNumberAndCompanyId(String invoiceNumber, UUID companyId);
    boolean existsByInvoiceNumberAndCompanyId(String invoiceNumber, UUID companyId);
    
    @Query("SELECT i FROM Invoice i WHERE i.companyId = :companyId AND i.deletedAt IS NULL " +
           "AND (CAST(:clientId AS string) IS NULL OR i.clientId = :clientId) " +
           "AND (CAST(:projectId AS string) IS NULL OR i.projectId = :projectId) " +
           "AND (CAST(:status AS string) IS NULL OR i.status = :status) " +
           "AND (:fromDate IS NULL OR i.issueDate >= :fromDate) " +
           "AND (:toDate IS NULL OR i.issueDate <= :toDate)")
    List<Invoice> findFilteredInvoices(@Param("companyId") UUID companyId, @Param("clientId") UUID clientId, @Param("projectId") UUID projectId, @Param("status") String status, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
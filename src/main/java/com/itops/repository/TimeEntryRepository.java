package com.itops.repository;

import com.itops.domain.TimeEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {      
    List<TimeEntry> findByCompanyId(UUID companyId);
    
    Optional<TimeEntry> findByUserIdAndIsActiveTrue(UUID userId);
    
    // Find time entries by user and date range
    @Query("SELECT t FROM TimeEntry t WHERE t.userId = :userId AND t.date >= :startDate AND t.date <= :endDate AND t.deletedAt IS NULL")
    List<TimeEntry> findByUserIdAndDateRange(@Param("userId") UUID userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Find time entries by project
    @Query("SELECT t FROM TimeEntry t WHERE t.projectId = :projectId AND t.deletedAt IS NULL")
    List<TimeEntry> findByProjectId(@Param("projectId") UUID projectId);
    
    // Find unbilled time entries for invoice generation (only from APPROVED timesheets)
    @Query(value = "SELECT t.* FROM time_entries t " +
           "WHERE t.company_id = :companyId " +
           "AND t.project_id IN :projectIds " +
           "AND t.date >= :fromDate " +
           "AND t.date <= :toDate " +
           "AND t.invoice_id IS NULL " +
           "AND (:billableOnly = false OR t.is_billable = true) " +
           "AND t.deleted_at IS NULL " +
           "AND EXISTS (" +
           "  SELECT 1 FROM timesheets ts " +
           "  WHERE ts.company_id = t.company_id " +
           "  AND ts.user_id = t.user_id " +
           "  AND t.date >= ts.week_start " +
           "  AND t.date < ts.week_start + INTERVAL '7 days' " +
           "  AND ts.status = 'APPROVED' " +
           "  AND ts.deleted_at IS NULL" +
           ") " +
           "ORDER BY t.date, t.user_id",
           nativeQuery = true)
    List<TimeEntry> findUnbilledEntriesForProjects(
        @Param("companyId") UUID companyId,
        @Param("projectIds") List<UUID> projectIds,
        @Param("fromDate") LocalDate fromDate,
        @Param("toDate") LocalDate toDate,
        @Param("billableOnly") Boolean billableOnly
    );
    
    // Update invoice ID for multiple entries (for bulk billing)
    @Modifying
    @Query("UPDATE TimeEntry t SET t.invoiceId = :invoiceId, t.billedAt = :billedAt " +
           "WHERE t.id IN :entryIds AND t.invoiceId IS NULL")
    int updateInvoiceIdForEntries(
        @Param("entryIds") List<UUID> entryIds,
        @Param("invoiceId") UUID invoiceId,
        @Param("billedAt") java.time.LocalDateTime billedAt
    );
}

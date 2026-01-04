package com.itops.repository;

import com.itops.domain.Timesheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TimesheetRepository extends JpaRepository<Timesheet, UUID> {
    
    // Find timesheet by user and week
    Optional<Timesheet> findByUserIdAndWeekStartAndDeletedAtIsNull(UUID userId, LocalDate weekStart);
    
    // Find all timesheets for a user
    List<Timesheet> findByUserIdAndDeletedAtIsNull(UUID userId);
    
    // Find all timesheets for company
    List<Timesheet> findByCompanyIdAndDeletedAtIsNull(UUID companyId);
    
    // Find timesheets by status
    List<Timesheet> findByCompanyIdAndStatusAndDeletedAtIsNull(UUID companyId, String status);
    
    // Find timesheets for specific users
    @Query("SELECT t FROM Timesheet t WHERE t.companyId = :companyId AND t.userId IN :userIds AND t.deletedAt IS NULL")
    List<Timesheet> findByCompanyIdAndUserIdIn(@Param("companyId") UUID companyId, @Param("userIds") List<UUID> userIds);
    
    // Find timesheets by week range
    @Query("SELECT t FROM Timesheet t WHERE t.companyId = :companyId AND t.weekStart >= :fromDate AND t.weekStart <= :toDate AND t.deletedAt IS NULL")
    List<Timesheet> findByCompanyIdAndWeekRange(@Param("companyId") UUID companyId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    // Find timesheets by status and week
    @Query("SELECT t FROM Timesheet t WHERE t.companyId = :companyId AND t.status = :status AND t.weekStart = :weekStart AND t.deletedAt IS NULL")
    List<Timesheet> findByCompanyIdAndStatusAndWeekStart(@Param("companyId") UUID companyId, @Param("status") String status, @Param("weekStart") LocalDate weekStart);
}

package com.itops.repository;

import com.itops.domain.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {
    
    // Find all leave requests for a user
    List<LeaveRequest> findByUserIdAndDeletedAtIsNull(UUID userId);
    
    // Find leave requests by status
    List<LeaveRequest> findByUserIdAndStatusAndDeletedAtIsNull(UUID userId, String status);
    
    // Find all leave requests for company
    List<LeaveRequest> findByCompanyIdAndDeletedAtIsNull(UUID companyId);
    
    // Find leave requests by status for company
    List<LeaveRequest> findByCompanyIdAndStatusAndDeletedAtIsNull(UUID companyId, String status);
    
    // Find leave requests for specific users
    @Query("SELECT l FROM LeaveRequest l WHERE l.companyId = :companyId AND l.userId IN :userIds AND l.deletedAt IS NULL")
    List<LeaveRequest> findByCompanyIdAndUserIdIn(@Param("companyId") UUID companyId, @Param("userIds") List<UUID> userIds);
    
    // Find leave requests by date range
    @Query("SELECT l FROM LeaveRequest l WHERE l.companyId = :companyId AND l.startDate <= :toDate AND l.endDate >= :fromDate AND l.deletedAt IS NULL")
    List<LeaveRequest> findByCompanyIdAndDateRange(@Param("companyId") UUID companyId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
    
    // Find approved leave requests for a user in date range
    @Query("SELECT l FROM LeaveRequest l WHERE l.userId = :userId AND l.status = 'APPROVED' AND l.startDate <= :toDate AND l.endDate >= :fromDate AND l.deletedAt IS NULL")
    List<LeaveRequest> findApprovedLeaveByUserAndDateRange(@Param("userId") UUID userId, @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}

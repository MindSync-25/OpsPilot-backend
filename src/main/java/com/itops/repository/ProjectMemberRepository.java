package com.itops.repository;

import com.itops.domain.ProjectMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    List<ProjectMember> findByCompanyId(UUID companyId);

    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.companyId = :companyId AND pm.deletedAt IS NULL")
    List<ProjectMember> findByProjectIdAndCompanyId(@Param("projectId") UUID projectId, @Param("companyId") UUID companyId);

    @Query("SELECT pm FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.user.id = :userId AND pm.companyId = :companyId AND pm.deletedAt IS NULL")
    Optional<ProjectMember> findByProjectIdAndUserIdAndCompanyId(
        @Param("projectId") UUID projectId, 
        @Param("userId") UUID userId, 
        @Param("companyId") UUID companyId
    );

    @Query("SELECT CASE WHEN COUNT(pm) > 0 THEN true ELSE false END FROM ProjectMember pm WHERE pm.project.id = :projectId AND pm.user.id = :userId AND pm.deletedAt IS NULL")
    boolean existsByProjectIdAndUserId(@Param("projectId") UUID projectId, @Param("userId") UUID userId);
}

package com.itops.repository;

import com.itops.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailAndCompanyId(String email, UUID companyId);
    Optional<User> findByNameIgnoreCaseAndCompanyId(String name, UUID companyId);
    List<User> findByCompanyId(UUID companyId);
    List<User> findByCompanyIdAndDeletedAtIsNull(UUID companyId);
    List<User> findByTeamId(UUID teamId);
    List<User> findByTeamIdAndDeletedAtIsNull(UUID teamId);
    List<User> findByCreatedByUserId(UUID createdByUserId);
}

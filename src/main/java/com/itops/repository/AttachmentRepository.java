package com.itops.repository;

import com.itops.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByEntityTypeAndEntityId(String entityType, UUID entityId);
    List<Attachment> findByEntityTypeAndEntityIdAndCompanyId(String entityType, UUID entityId, UUID companyId);
    List<Attachment> findByCompanyId(UUID companyId);
    void deleteByEntityTypeAndEntityIdAndCompanyId(String entityType, UUID entityId, UUID companyId);
}

package com.itops.repository;

import com.itops.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findByCompanyIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            UUID companyId, 
            Comment.EntityType entityType, 
            UUID entityId
    );
    
    List<Comment> findByCompanyIdAndEntityTypeAndEntityIdOrderByCreatedAtAsc(
            UUID companyId, 
            Comment.EntityType entityType, 
            UUID entityId
    );
    
    // Methods for deletion by entity
    List<Comment> findByEntityTypeAndEntityId(Comment.EntityType entityType, UUID entityId);
}

package com.itops.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectMemberResponse {

    private UUID id;
    private UUID userId;
    private String userName;
    private String userEmail;
    private String userDesignation;
    private String userRole;  // Company role (ADMIN, USER, etc.)
    private String roleInProject;  // Project-specific role
    private LocalDateTime createdAt;
}

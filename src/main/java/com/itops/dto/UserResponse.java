package com.itops.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private String role;
    private UUID teamId;
    private String designation;
    private java.math.BigDecimal hourlyRate;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private UUID companyId;
    private UUID createdByUserId;
    private UUID managerUserId;
}

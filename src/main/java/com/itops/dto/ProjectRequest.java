package com.itops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class ProjectRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private UUID clientId;

    private String status = "PLANNING";

    private String priority = "MEDIUM";

    private String startDate;

    private String endDate;
    
    private UUID projectOwnerId;
}

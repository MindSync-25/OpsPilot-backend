package com.itops.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.UUID;

@Data
public class CreatePhaseRequest {

    @NotBlank(message = "Phase name is required")
    private String name;

    private String description;

    private Integer sortOrder = 0;

    private String status = "TODO"; // TODO, ACTIVE, COMPLETED, ARCHIVED

    private UUID teamId;
}

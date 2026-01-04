package com.itops.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class UpdatePhaseRequest {

    private String name;

    private String description;

    private Integer sortOrder;

    private String status; // ACTIVE, COMPLETED, ARCHIVED

    private UUID teamId;
}

package com.itops.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TeamRequest {
    private String name;
    private UUID leadUserId;
}

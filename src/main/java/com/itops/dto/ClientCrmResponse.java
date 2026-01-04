package com.itops.dto;

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
public class ClientCrmResponse {
    private UUID id;
    private UUID clientId;
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
    private String status;
    private String leadStage;
    private String notes;
    private UUID ownerId;
    private String ownerName;
    private LocalDateTime nextFollowUp;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

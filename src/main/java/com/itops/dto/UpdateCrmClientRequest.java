package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateCrmClientRequest {
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
    private String status;
    private String leadStage;
    private String notes;
    private String nextFollowUp;
    private java.util.UUID ownerId; // Allow changing the owner
}

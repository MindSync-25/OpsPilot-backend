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
public class ClientResponse {
    private UUID id;
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
    private String status;
    private UUID companyId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

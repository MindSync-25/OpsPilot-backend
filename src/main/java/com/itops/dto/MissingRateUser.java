package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MissingRateUser {
    
    private UUID userId;
    
    private String name;
    
    private String email;
    
    private String message; // e.g., "User has no hourly rate configured"
}

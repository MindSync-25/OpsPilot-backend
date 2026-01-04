package com.itops.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Data
@Builder
@AllArgsConstructor
public class LoginResponse {
    private String accessToken;
    private String refreshToken;
    private String tokenType = "Bearer";
    private UserInfo user;
    
    @Data
    @Builder
    public static class UserInfo {
        private UUID id;
        private String name;
        private String email;
        private String role;
        private UUID companyId;
        private UUID teamId;
    }
}

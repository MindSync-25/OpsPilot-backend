package com.itops.dto;

import jakarta.validation.constraints.Email;
import lombok.Data;

@Data
public class UpdateUserProfileRequest {
    private String name;
    
    @Email
    private String email;
    
    private String phone;
    private String designation;
}

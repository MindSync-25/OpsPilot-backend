package com.itops.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLeaveStatusRequest {
    @NotNull(message = "Status is required")
    @Pattern(regexp = "APPROVED|REJECTED|CANCELLED", message = "Status must be APPROVED, REJECTED, or CANCELLED")
    private String status;
    
    private String decisionNote;
}

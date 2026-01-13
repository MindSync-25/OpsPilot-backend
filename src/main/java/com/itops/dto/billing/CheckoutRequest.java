package com.itops.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutRequest {
    
    @NotBlank(message = "Plan code is required")
    private String planCode;
    
    @NotBlank(message = "Billing cycle is required")
    @Pattern(regexp = "MONTHLY|YEARLY", message = "Billing cycle must be MONTHLY or YEARLY")
    private String billingCycle;
}

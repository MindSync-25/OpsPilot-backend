package com.itops.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckoutResponse {
    private String keyId;
    private String subscriptionId;
    private String customerId;
    private String planCode;
    private String billingCycle;
    private BigDecimal amount;
    private String currency;
    private String companyName;
    private String userName;
    private String userEmail;
}

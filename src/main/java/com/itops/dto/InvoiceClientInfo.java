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
public class InvoiceClientInfo {
    private UUID id;
    private String name;
    private String contactName;
    private String email;
    private String phone;
    private String address;
}

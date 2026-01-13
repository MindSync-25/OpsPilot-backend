package com.itops.dto;

import lombok.Data;

@Data
public class UpdateCompanyRequest {
    private String name;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String phone;
}

package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clients", indexes = {
    @Index(name = "idx_clients_company_id", columnList = "company_id"),
    @Index(name = "idx_clients_email", columnList = "email"),
    @Index(name = "idx_clients_status", columnList = "status"),
    @Index(name = "idx_clients_deleted_at", columnList = "deleted_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client extends BaseEntity {
    
    @Column(nullable = false, length = 255)
    private String name;
    
    @Column(name = "contact_name", length = 255)
    private String contactName;
    
    @Column(length = 255)
    private String email;
    
    @Column(length = 50)
    private String phone;
    
    @Column(columnDefinition = "TEXT")
    private String address;
    
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String status = "ACTIVE"; // ACTIVE, INACTIVE, PROSPECT
}

package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole role;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "team_id")
    private UUID teamId;

    @Column(length = 150)
    private String designation;

    @Column(name = "hourly_rate", precision = 10, scale = 2)
    private java.math.BigDecimal hourlyRate;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "manager_user_id")
    private UUID managerUserId;

    public enum UserRole {
        TOP_USER, SUPER_USER, ADMIN, USER, CLIENT
    }
}

package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "teams")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Team extends BaseEntity {

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "lead_user_id")
    private UUID leadUserId;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;
}

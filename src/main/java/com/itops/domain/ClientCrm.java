package com.itops.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "client_crm")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClientCrm {
    @Id
    @GeneratedValue
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private Client client;

    @Column(name = "lead_stage", nullable = false, length = 32)
    private String leadStage; // PROSPECT, CONTACTED, PROPOSAL_SENT, WON, LOST

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "owner_id")
    private UUID ownerId; // User who owns/manages this lead

    @Column(name = "next_follow_up")
    private LocalDateTime nextFollowUp; // Next scheduled follow-up date

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
}

package com.nagarro.eclaims.claims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Immutable audit trail. Append-only — never updated or deleted. */
@Entity
@Table(name = "claim_status_history")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClaimStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claim_id", nullable = false, updatable = false)
    private Claim claim;

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status", length = 30)
    private ClaimStatus oldStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, length = 30)
    private ClaimStatus newStatus;

    @Column(name = "changed_by", length = 150)
    private String changedBy;

    @Column(name = "note")
    private String note;

    @Column(name = "event_source", length = 50)
    private String eventSource;

    @CreationTimestamp
    @Column(name = "changed_at", updatable = false)
    private LocalDateTime changedAt;
}

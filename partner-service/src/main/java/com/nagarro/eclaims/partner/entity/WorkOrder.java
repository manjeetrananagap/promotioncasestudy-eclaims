package com.nagarro.eclaims.partner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/** Tracks a repair job at a specific workshop for a claim. */
@Entity
@Table(name = "work_orders")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WorkOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "claim_id", nullable = false)
    private UUID claimId;

    @Column(name = "claim_number", length = 25)
    private String claimNumber;

    /** FK to workshops table within partner_db */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workshop_id", nullable = false)
    private Workshop workshop;

    /** NEW | IN_PROGRESS | COMPLETED | CANCELLED */
    @Column(nullable = false, length = 30)
    @Builder.Default
    private String status = "NEW";

    /**
     * Current repair milestone, updated by workshop staff:
     * VEHICLE_RECEIVED → DISASSEMBLY → PARTS_ORDERED →
     * REPAIR_IN_PROGRESS → QUALITY_CHECK → READY_FOR_PICKUP
     */
    @Column(name = "current_milestone", length = 50)
    private String currentMilestone;

    @Column(name = "appointment_at")
    private LocalDateTime appointmentAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}

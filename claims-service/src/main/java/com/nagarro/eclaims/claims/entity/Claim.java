package com.nagarro.eclaims.claims.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * JPA Entity for an insurance claim. Maps to {@code claims} table in {@code claims_db}.
 *
 * DDL is owned by Flyway — Hibernate is set to {@code validate} only.
 * Never add @Column(columnDefinition) that conflicts with Flyway SQL.
 */
@Entity
@Table(name = "claims")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@ToString(exclude = "statusHistory")
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "claim_number", unique = true, nullable = false, length = 25)
    private String claimNumber;

    @Column(name = "policy_id", nullable = false, length = 50)
    private String policyId;

    @Column(name = "policy_holder_name", nullable = false, length = 150)
    private String policyHolderName;

    @Column(name = "vehicle_reg", nullable = false, length = 20)
    private String vehicleReg;

    @Column(name = "vehicle_make", length = 50)
    private String vehicleMake;

    @Column(name = "vehicle_model", length = 50)
    private String vehicleModel;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private ClaimStatus status = ClaimStatus.SUBMITTED;

    @Column(name = "accident_lat", precision = 10, scale = 8)
    private BigDecimal accidentLat;

    @Column(name = "accident_lng", precision = 11, scale = 8)
    private BigDecimal accidentLng;

    @Column(name = "accident_address")
    private String accidentAddress;

    @Column(name = "incident_date", nullable = false)
    private LocalDate incidentDate;

    @Column(name = "incident_description", nullable = false)
    private String incidentDescription;

    @Column(name = "estimated_amount", precision = 12, scale = 2)
    private BigDecimal estimatedAmount;

    @Column(name = "approved_amount", precision = 12, scale = 2)
    private BigDecimal approvedAmount;

    @Column(name = "deductible_amount", precision = 12, scale = 2)
    private BigDecimal deductibleAmount;

    @Column(name = "insurer_contribution", precision = 12, scale = 2)
    private BigDecimal insurerContribution;

    @Column(name = "customer_contribution", precision = 12, scale = 2)
    private BigDecimal customerContribution;

    @Column(name = "workflow_instance_id", length = 100)
    private String workflowInstanceId;

    /** UUID ref to partner_db.surveyors — deliberately no @ManyToOne (cross-service) */
    @Column(name = "assigned_surveyor_id")
    private UUID assignedSurveyorId;

    /** UUID ref to partner_db.workshops — deliberately no @ManyToOne (cross-service) */
    @Column(name = "assigned_workshop_id")
    private UUID assignedWorkshopId;

    @Column(name = "submitted_by_user_id", nullable = false, length = 100)
    private String submittedByUserId;

    @Column(name = "submitted_by_name", length = 150)
    private String submittedByName;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false)
    @Builder.Default
    private Boolean deleted = false;

    /** Append-only audit history. LAZY — load explicitly when needed. */
    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL,
               fetch = FetchType.LAZY, orphanRemoval = true)
    @OrderBy("changedAt ASC")
    @Builder.Default
    private List<ClaimStatusHistory> statusHistory = new ArrayList<>();

    /**
     * Transitions to a new status AND appends an audit history entry.
     * All status changes MUST go through this method.
     */
    public void transitionTo(ClaimStatus newStatus, String changedBy, String note, String source) {
        this.statusHistory.add(
            ClaimStatusHistory.builder()
                .claim(this)
                .oldStatus(this.status)
                .newStatus(newStatus)
                .changedBy(changedBy)
                .note(note)
                .eventSource(source)
                .build()
        );
        this.status = newStatus;
    }
}

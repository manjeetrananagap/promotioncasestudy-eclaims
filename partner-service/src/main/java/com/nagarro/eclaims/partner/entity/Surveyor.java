package com.nagarro.eclaims.partner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Surveyor available for geo-radius assignment. Maps to partner_db.surveyors. */
@Entity
@Table(name = "surveyors")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Surveyor {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(unique = true, nullable = false, length = 200)
    private String email;

    @Column(length = 30)
    private String phone;

    /** Surveyor base location — used for Haversine geo-radius distance calculation */
    @Column(name = "base_lat", nullable = false, precision = 10, scale = 8)
    private BigDecimal baseLat;

    @Column(name = "base_lng", nullable = false, precision = 11, scale = 8)
    private BigDecimal baseLng;

    @Column(length = 100)
    private String city;

    /** AVAILABLE | ASSIGNED | OFF_DUTY */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "AVAILABLE";

    @Column(name = "active_claims", nullable = false)
    @Builder.Default
    private Integer activeClaims = 0;

    @Column(name = "max_claims", nullable = false)
    @Builder.Default
    private Integer maxClaims = 5;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isAvailable() {
        return "AVAILABLE".equals(this.status) && this.activeClaims < this.maxClaims;
    }
}

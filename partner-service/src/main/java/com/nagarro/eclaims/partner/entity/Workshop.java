package com.nagarro.eclaims.partner.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Certified repair workshop. Maps to partner_db.workshops. */
@Entity
@Table(name = "workshops")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Workshop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private String address;

    @Column(length = 100)
    private String city;

    /** Workshop geo-location for radius search */
    @Column(nullable = false, precision = 10, scale = 8)
    private BigDecimal lat;

    @Column(nullable = false, precision = 11, scale = 8)
    private BigDecimal lng;

    @Column(length = 30)
    private String phone;

    @Column(length = 200)
    private String email;

    @Column(name = "certification_no", length = 50)
    private String certificationNo;

    @Column(name = "certification_expiry")
    private LocalDate certificationExpiry;

    /** Comma-separated: BODY,ENGINE,ELECTRICAL,GLASS,PAINT */
    @Column(name = "repair_types", length = 200)
    private String repairTypes;

    /** Comma-separated: TOYOTA,HONDA,FORD */
    @Column(name = "vehicle_brands", length = 200)
    private String vehicleBrands;

    @Column(name = "weekly_capacity", nullable = false)
    @Builder.Default
    private Integer weeklyCapacity = 10;

    @Column(name = "current_load", nullable = false)
    @Builder.Default
    private Integer currentLoad = 0;

    /** 0.0 to 5.0 SLA performance score */
    @Column(name = "sla_score", nullable = false, precision = 3, scale = 1)
    @Builder.Default
    private BigDecimal slaScore = BigDecimal.valueOf(4.0);

    /** ACTIVE | INACTIVE | SUSPENDED */
    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "ACTIVE";

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public boolean isAvailable() {
        return "ACTIVE".equals(this.status) && this.currentLoad < this.weeklyCapacity;
    }

    public boolean isCertificationValid() {
        return certificationExpiry == null || !certificationExpiry.isBefore(LocalDate.now());
    }
}

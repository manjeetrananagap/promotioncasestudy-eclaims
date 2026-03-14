package com.nagarro.eclaims.document.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * JPA Entity representing document metadata stored in the document_db.
 *
 * <p>The actual binary file is stored in MinIO object storage.
 * This entity holds the reference (bucket + key) plus metadata needed
 * for claims processing, fraud detection (EXIF geo-coordinates), and compliance.</p>
 *
 * <p>Maps to the {@code documents} table in {@code document_db}.
 * DDL managed by Flyway. Hibernate is set to {@code validate} mode.</p>
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;

    /**
     * UUID of the associated claim in claims_db.
     * Not a JPA @ManyToOne — services are decoupled, no cross-DB foreign keys.
     */
    @Column(name = "claim_id", nullable = false, updatable = false)
    private UUID claimId;

    /**
     * Type of document — drives storage bucket selection and retention period.
     * Stored as String for forward compatibility.
     */
    @Column(name = "document_type", nullable = false, length = 50)
    private String documentType;

    // ── Storage reference ─────────────────────────────────────────────────────

    @Column(name = "original_name", length = 255)
    private String originalName;

    /** MinIO bucket name (eclaims-documents, eclaims-photos, eclaims-reports) */
    @Column(name = "storage_bucket", nullable = false, length = 100)
    private String storageBucket;

    /** MinIO object key — path within the bucket e.g. {claimId}/ACCIDENT_PHOTO/{uuid}.jpg */
    @Column(name = "storage_key", nullable = false, length = 500)
    private String storageKey;

    /** Cached pre-signed URL — refreshed on demand, expires after 1 hour */
    @Column(name = "storage_url", columnDefinition = "TEXT")
    private String storageUrl;

    // ── File metadata ─────────────────────────────────────────────────────────

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    // ── EXIF GPS metadata (photos only) ──────────────────────────────────────
    /** GPS latitude from photo EXIF — compared to claim accident_lat for fraud detection */
    @Column(name = "photo_lat", precision = 10, scale = 8)
    private BigDecimal photoLat;

    /** GPS longitude from photo EXIF */
    @Column(name = "photo_lng", precision = 11, scale = 8)
    private BigDecimal photoLng;

    @Column(name = "exif_captured_at")
    private LocalDateTime exifCapturedAt;

    // ── Upload provenance ─────────────────────────────────────────────────────

    @Column(name = "uploaded_by_user_id", length = 100)
    private String uploadedByUserId;

    /** CUSTOMER_PORTAL | SURVEYOR_APP | ADJUSTOR_PORTAL | SYSTEM */
    @Column(name = "upload_source", length = 30)
    private String uploadSource;

    /** Documents must be retained for 7 years for regulatory compliance */
    @Column(name = "retention_until")
    private LocalDate retentionUntil;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}

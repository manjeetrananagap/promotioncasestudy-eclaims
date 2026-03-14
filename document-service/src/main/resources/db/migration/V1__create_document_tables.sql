-- =============================================================================
-- V1__create_document_tables.sql
-- Document Service schema — managed by Flyway
-- =============================================================================

-- ── DOCUMENTS table ───────────────────────────────────────────────────────────
-- Stores metadata for every file associated with a claim.
-- Actual binary files are stored in MinIO; this table only holds references.
CREATE TABLE documents (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Link to the claim (loose coupling — no FK to claims_db)
    claim_id        UUID         NOT NULL,

    -- Document classification
    document_type   VARCHAR(50)  NOT NULL,
    -- Allowed values: ACCIDENT_PHOTO, POLICE_REPORT, VEHICLE_RC,
    --                 INSURANCE_POLICY, ASSESSMENT_REPORT, APPROVAL_LETTER,
    --                 REPAIR_INVOICE, PAYMENT_RECEIPT

    -- Original filename from upload
    original_name   VARCHAR(255),

    -- MinIO storage reference
    storage_bucket  VARCHAR(100) NOT NULL,  -- e.g. eclaims-photos
    storage_key     VARCHAR(500) NOT NULL,  -- Object key: {claimId}/{docType}/{uuid}.jpg
    storage_url     TEXT,                   -- Pre-signed or public URL (refreshed on demand)

    -- File metadata
    content_type    VARCHAR(100),           -- MIME type e.g. image/jpeg
    file_size_bytes BIGINT,

    -- GPS metadata extracted from photo EXIF (for fraud detection)
    -- Compared against claim's accident_lat/lng in fraud check
    photo_lat       DECIMAL(10, 8),
    photo_lng       DECIMAL(11, 8),
    exif_captured_at TIMESTAMPTZ,

    -- Upload provenance
    uploaded_by_user_id VARCHAR(100),
    upload_source   VARCHAR(30),
    -- Allowed values: CUSTOMER_PORTAL, SURVEYOR_APP, ADJUSTOR_PORTAL, SYSTEM

    -- Regulatory retention — documents must be kept for 7 years
    retention_until DATE,

    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indexes for common access patterns
CREATE INDEX idx_documents_claim_id   ON documents(claim_id);
CREATE INDEX idx_documents_type       ON documents(document_type);
CREATE INDEX idx_documents_created_at ON documents(created_at DESC);

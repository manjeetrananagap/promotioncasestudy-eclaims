-- =============================================================================
-- V1__create_claims_tables.sql
-- Managed by Flyway. Never edit this file after first run.
-- To change schema, create V2__... file.
-- =============================================================================

CREATE TABLE claims (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_number          VARCHAR(25)  UNIQUE NOT NULL,
    policy_id             VARCHAR(50)  NOT NULL,
    policy_holder_name    VARCHAR(150) NOT NULL,
    vehicle_reg           VARCHAR(20)  NOT NULL,
    vehicle_make          VARCHAR(50),
    vehicle_model         VARCHAR(50),
    -- Lifecycle status (see ClaimStatus enum)
    status                VARCHAR(30)  NOT NULL DEFAULT 'SUBMITTED',
    -- GPS accident location captured at FNOL
    accident_lat          DECIMAL(10,8),
    accident_lng          DECIMAL(11,8),
    accident_address      TEXT,
    incident_date         DATE         NOT NULL,
    incident_description  TEXT         NOT NULL,
    -- Financial (populated during adjudication)
    estimated_amount      DECIMAL(12,2),
    approved_amount       DECIMAL(12,2),
    deductible_amount     DECIMAL(12,2),
    insurer_contribution  DECIMAL(12,2),
    customer_contribution DECIMAL(12,2),
    -- Cross-service refs (loose coupling — UUIDs only, no FK constraints)
    workflow_instance_id  VARCHAR(100),
    assigned_surveyor_id  UUID,
    assigned_workshop_id  UUID,
    -- Submitter
    submitted_by_user_id  VARCHAR(100) NOT NULL,
    submitted_by_name     VARCHAR(150),
    -- Audit
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted               BOOLEAN      NOT NULL DEFAULT FALSE
);

-- Immutable audit trail — one row per status transition
CREATE TABLE claim_status_history (
    id           UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id     UUID        NOT NULL REFERENCES claims(id) ON DELETE CASCADE,
    old_status   VARCHAR(30),
    new_status   VARCHAR(30) NOT NULL,
    changed_by   VARCHAR(150),
    note         TEXT,
    event_source VARCHAR(50),   -- REST_API | KAFKA_EVENT | SCHEDULER
    changed_at   TIMESTAMPTZ    NOT NULL DEFAULT NOW()
);

-- Auto-update updated_at
CREATE OR REPLACE FUNCTION fn_set_updated_at()
RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_claims_updated_at
    BEFORE UPDATE ON claims
    FOR EACH ROW EXECUTE FUNCTION fn_set_updated_at();

-- Indexes
CREATE INDEX idx_claims_status         ON claims(status);
CREATE INDEX idx_claims_policy_id      ON claims(policy_id);
CREATE INDEX idx_claims_user_id        ON claims(submitted_by_user_id);
CREATE INDEX idx_claims_created_at     ON claims(created_at DESC);
CREATE INDEX idx_history_claim_id      ON claim_status_history(claim_id, changed_at DESC);

-- Sequence for human-readable claim numbers: CLM-2024-000001
CREATE SEQUENCE claim_number_seq START WITH 1 INCREMENT BY 1 NO MAXVALUE CACHE 1;

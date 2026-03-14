-- V1__create_partner_tables.sql

CREATE TABLE surveyors (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(200) UNIQUE NOT NULL,
    phone           VARCHAR(30),
    base_lat        DECIMAL(10,8) NOT NULL,
    base_lng        DECIMAL(11,8) NOT NULL,
    city            VARCHAR(100),
    -- AVAILABLE | ASSIGNED | OFF_DUTY
    status          VARCHAR(20)  NOT NULL DEFAULT 'AVAILABLE',
    active_claims   INTEGER      NOT NULL DEFAULT 0,
    max_claims      INTEGER      NOT NULL DEFAULT 5,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE workshops (
    id                   UUID          PRIMARY KEY DEFAULT gen_random_uuid(),
    name                 VARCHAR(150)  NOT NULL,
    address              TEXT          NOT NULL,
    city                 VARCHAR(100),
    lat                  DECIMAL(10,8) NOT NULL,
    lng                  DECIMAL(11,8) NOT NULL,
    phone                VARCHAR(30),
    email                VARCHAR(200),
    certification_no     VARCHAR(50),
    certification_expiry DATE,
    -- Stored as comma-separated for simplicity in POC
    repair_types         VARCHAR(200),   -- e.g. BODY,ENGINE,ELECTRICAL
    vehicle_brands       VARCHAR(200),   -- e.g. TOYOTA,HONDA,FORD
    weekly_capacity      INTEGER         NOT NULL DEFAULT 10,
    current_load         INTEGER         NOT NULL DEFAULT 0,
    sla_score            DECIMAL(3,1)    NOT NULL DEFAULT 4.0,
    -- ACTIVE | INACTIVE | SUSPENDED
    status               VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE',
    created_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE work_orders (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    claim_id        UUID         NOT NULL,
    claim_number    VARCHAR(25),
    workshop_id     UUID         NOT NULL REFERENCES workshops(id),
    -- NEW | IN_PROGRESS | COMPLETED | CANCELLED
    status          VARCHAR(30)  NOT NULL DEFAULT 'NEW',
    -- Repair milestones: VEHICLE_RECEIVED|DISASSEMBLY|PARTS_ORDERED|REPAIR_IN_PROGRESS|QUALITY_CHECK|READY_FOR_PICKUP
    current_milestone VARCHAR(50),
    appointment_at  TIMESTAMPTZ,
    completed_at    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- Indexes
CREATE INDEX idx_surveyors_status ON surveyors(status);
CREATE INDEX idx_workshops_status ON workshops(status);
CREATE INDEX idx_work_orders_claim ON work_orders(claim_id);

-- Auto-update updated_at triggers
CREATE OR REPLACE FUNCTION fn_partner_updated_at() RETURNS TRIGGER AS $$
BEGIN NEW.updated_at = NOW(); RETURN NEW; END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_surveyors_updated_at BEFORE UPDATE ON surveyors FOR EACH ROW EXECUTE FUNCTION fn_partner_updated_at();
CREATE TRIGGER trg_workshops_updated_at BEFORE UPDATE ON workshops FOR EACH ROW EXECUTE FUNCTION fn_partner_updated_at();
CREATE TRIGGER trg_work_orders_updated_at BEFORE UPDATE ON work_orders FOR EACH ROW EXECUTE FUNCTION fn_partner_updated_at();

-- ── Seed data — surveyors and workshops for Delhi NCR region ──────────────────
INSERT INTO surveyors (name, email, phone, base_lat, base_lng, city) VALUES
    ('Arjun Sharma',  'arjun.surveyor@eclaims.local',  '+91-9876543210', 28.6139,  77.2090, 'Delhi'),
    ('Priya Mehta',   'priya.surveyor@eclaims.local',   '+91-9876543211', 28.5355,  77.3910, 'Noida'),
    ('Ravi Kumar',    'ravi.surveyor@eclaims.local',    '+91-9876543212', 28.7041,  77.1025, 'Delhi'),
    ('Sunita Patel',  'sunita.surveyor@eclaims.local',  '+91-9876543213', 28.4595,  77.0266, 'Gurugram'),
    ('Amit Singh',    'amit.surveyor@eclaims.local',    '+91-9876543214', 28.6304,  77.2177, 'Delhi');

INSERT INTO workshops (name, address, city, lat, lng, certification_no, repair_types, vehicle_brands, sla_score) VALUES
    ('AutoCare Delhi',     'Connaught Place, New Delhi',   'Delhi',    28.6315, 77.2167, 'CERT-DL-001', 'BODY,ENGINE',        'TOYOTA,HONDA,MARUTI',   4.5),
    ('SpeedFix Noida',     'Sector 18, Noida',             'Noida',    28.5706, 77.3272, 'CERT-NR-002', 'BODY,ELECTRICAL',    'FORD,HYUNDAI,TATA',     4.2),
    ('Premier Workshop',   'MG Road, Gurugram',            'Gurugram', 28.4808, 77.0918, 'CERT-GR-003', 'BODY,ENGINE,GLASS',  'BMW,AUDI,MERCEDES',     4.8),
    ('QuickRepair Hub',    'Dwarka Sector 6, New Delhi',   'Delhi',    28.5921, 77.0460, 'CERT-DL-004', 'BODY,ENGINE',        'MARUTI,TATA,MAHINDRA',  4.0),
    ('NorthStar Auto',     'Rohini, New Delhi',            'Delhi',    28.7337, 77.1151, 'CERT-DL-005', 'BODY,ENGINE,PAINT',  'HONDA,HYUNDAI,TOYOTA',  4.3);

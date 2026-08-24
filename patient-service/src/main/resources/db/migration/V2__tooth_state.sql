-- data-model.md ToothState — one row per tooth per patient, pre-created at PatientRecord
-- creation time (research.md #3), so "new record ⇒ all teeth healthy" needs no null-handling.
CREATE TYPE tooth_status AS ENUM ('HEALTHY', 'SICK');

CREATE TABLE tooth_state (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id  UUID NOT NULL REFERENCES patient_record (id),
    tooth_number        SMALLINT NOT NULL,
    status                tooth_status NOT NULL DEFAULT 'HEALTHY',
    updated_at            TIMESTAMPTZ NOT NULL,
    -- No DB-level FK to staff_account (owned by auth-service) — same cross-service reasoning as
    -- patient_record.created_by/updated_by (research.md #5).
    updated_by            UUID
);

-- Exactly one row per tooth per patient (FR-005/FR-006, US2 Acceptance Scenario 3).
CREATE UNIQUE INDEX idx_tooth_state_patient_tooth ON tooth_state (patient_record_id, tooth_number);
CREATE INDEX idx_tooth_state_patient_record_id ON tooth_state (patient_record_id);

GRANT SELECT, INSERT, UPDATE, DELETE ON tooth_state TO patient_service_app;

-- data-model.md AllergyEntry/MedicationEntry/ChronicConditionEntry — three per-patient clinical
-- sub-resources (feature 004), same FK/ownership style as V2__tooth_state.sql: real FK to
-- patient_record (owned by this service), no DB-level FK to staff_account (owned by auth-service,
-- research.md #5 of 002). Each table carries the shared, technical record_status correction flag
-- (research.md #3) plus a nullable, self-referencing supersedes_entry_id — independent of any
-- clinical-status field an entity may also carry (data-model.md, Clarifications Session
-- 2026-08-29 Q1).
CREATE TYPE medical_history_record_status AS ENUM ('CURRENT', 'SUPERSEDED');
CREATE TYPE allergy_severity AS ENUM ('CRITICAL', 'MODERATE');
CREATE TYPE chronic_condition_status AS ENUM ('ACTIVE', 'PAST');

CREATE TABLE allergy_entry (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id    UUID NOT NULL REFERENCES patient_record (id),
    substance             TEXT NOT NULL,
    reaction_type         TEXT NOT NULL,
    severity              allergy_severity NOT NULL,
    record_status         medical_history_record_status NOT NULL DEFAULT 'CURRENT',
    supersedes_entry_id  UUID REFERENCES allergy_entry (id),
    created_at            TIMESTAMPTZ NOT NULL,
    created_by            UUID NOT NULL
);

CREATE TABLE medication_entry (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id    UUID NOT NULL REFERENCES patient_record (id),
    name                  TEXT NOT NULL,
    dosage                TEXT NOT NULL,
    start_date            DATE NOT NULL,
    record_status         medical_history_record_status NOT NULL DEFAULT 'CURRENT',
    supersedes_entry_id  UUID REFERENCES medication_entry (id),
    created_at            TIMESTAMPTZ NOT NULL,
    created_by            UUID NOT NULL
);

CREATE TABLE chronic_condition_entry (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_record_id    UUID NOT NULL REFERENCES patient_record (id),
    name                  TEXT NOT NULL,
    clinical_status       chronic_condition_status NOT NULL,
    diagnosis_date        DATE NOT NULL,
    record_status         medical_history_record_status NOT NULL DEFAULT 'CURRENT',
    supersedes_entry_id  UUID REFERENCES chronic_condition_entry (id),
    created_at            TIMESTAMPTZ NOT NULL,
    created_by            UUID NOT NULL
);

-- Default-view query (record_status = 'CURRENT') is the hot path for every read (FR-006/SC-004);
-- history reads scan the whole per-patient set regardless, so a plain patient_record_id index
-- covers both.
CREATE INDEX idx_allergy_entry_patient_record_id ON allergy_entry (patient_record_id);
CREATE INDEX idx_medication_entry_patient_record_id ON medication_entry (patient_record_id);
CREATE INDEX idx_chronic_condition_entry_patient_record_id
    ON chronic_condition_entry (patient_record_id);

GRANT SELECT, INSERT, UPDATE ON allergy_entry TO patient_service_app;
GRANT SELECT, INSERT, UPDATE ON medication_entry TO patient_service_app;
GRANT SELECT, INSERT, UPDATE ON chronic_condition_entry TO patient_service_app;

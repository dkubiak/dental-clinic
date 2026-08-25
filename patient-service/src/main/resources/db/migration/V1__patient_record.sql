-- Own Flyway schema-history table (research.md #7), separate from auth-service's. Idempotently
-- creates patient_service_app so this migration can run first if auth-service's V10 hasn't yet
-- (either order works — both use the same idempotent pattern V5__audit_log.sql established).
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'patient_service_app') THEN
        CREATE ROLE patient_service_app WITH LOGIN;
    END IF;
END
$$;

-- data-model.md PatientRecord. created_by/updated_by store a staff_account.id value but carry NO
-- DB-level FK — staff_account is owned by auth-service's separate migration history, so a
-- cross-service FK would couple the two services' schema-migration ordering (research.md #5).
CREATE TABLE patient_record (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    first_name            TEXT NOT NULL,
    last_name             TEXT NOT NULL,
    date_of_birth         DATE NOT NULL,
    pesel                 CHAR(11),
    address_street        TEXT NOT NULL,
    address_building_no   TEXT NOT NULL,
    address_postal_code   TEXT NOT NULL,
    address_city          TEXT NOT NULL,
    created_at            TIMESTAMPTZ NOT NULL,
    created_by            UUID NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    updated_by            UUID
);

-- FR-003: reject a new record if its PESEL already exists — partial (only when PESEL is
-- present), since PESEL is optional and PESEL-less records are never deduplicated (FR-002/FR-003,
-- spec.md Edge Cases, accepted risk).
CREATE UNIQUE INDEX idx_patient_record_pesel ON patient_record (pesel) WHERE pesel IS NOT NULL;

-- FR-012: case-insensitive last-name search.
CREATE INDEX idx_patient_record_last_name ON patient_record (lower(last_name));

GRANT SELECT, INSERT, UPDATE, DELETE ON patient_record TO patient_service_app;

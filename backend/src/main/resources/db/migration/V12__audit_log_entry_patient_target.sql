-- Extends audit_log_entry (owned by auth-service) for feature 002-patient-records instead of
-- creating a second, parallel audit table (research.md #5) — one hash chain for the whole
-- system. No DB-level FK to patient_record.id: that table is owned by patient-service's own,
-- separate Flyway migration history, so a cross-service FK would couple the two services'
-- schema-migration ordering (research.md #5, revised after the services were split).
ALTER TABLE audit_log_entry ADD COLUMN target_patient_record_id UUID;

-- patient_service_app needs to write to the same tamper-evident audit trail auth_service_app
-- writes to (V5__audit_log.sql) — same INSERT/SELECT-only boundary, UPDATE/DELETE never granted.
GRANT SELECT, INSERT ON audit_log_entry TO patient_service_app;
GRANT USAGE, SELECT ON SEQUENCE audit_log_entry_id_seq TO patient_service_app;

-- Cross-service session validation (research.md #7, plan.md Risk Tier & Availability):
-- patient-service reads auth-service's Spring Session JDBC tables to authenticate requests and
-- resolve the caller's role, the same mechanism that already lets auth-service's own ≥2
-- replicas share sessions. UPDATE is granted solely so patient-service can bump
-- last_access_time on read, matching Spring Session JDBC's own read-path behavior — never
-- INSERT/DELETE, since session lifecycle (create/expire) stays exclusively auth-service's
-- responsibility.
GRANT SELECT, UPDATE ON spring_session, spring_session_attributes TO patient_service_app;

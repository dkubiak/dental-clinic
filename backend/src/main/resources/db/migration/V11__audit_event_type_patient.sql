-- New audit_event_type values for feature 002-patient-records (data-model.md AuditLogEntry
-- extension). PATIENT_RECORD_VIEWED / TOOTH_CHART_VIEWED cover FR-007/SC-003's requirement that
-- READ operations on patient/clinical data are audited too, not just writes (added during
-- /speckit-analyze remediation, not part of the original research.md #5 draft list). Adding
-- several values to the same enum within one transaction is safe in Postgres as long as none of
-- them are *used* (e.g. in an INSERT) within that same transaction — this migration only adds
-- values.
ALTER TYPE audit_event_type ADD VALUE 'PATIENT_RECORD_CREATED';
ALTER TYPE audit_event_type ADD VALUE 'PATIENT_RECORD_UPDATED';
ALTER TYPE audit_event_type ADD VALUE 'PATIENT_RECORD_VIEWED';
ALTER TYPE audit_event_type ADD VALUE 'TOOTH_STATE_CHANGED';
ALTER TYPE audit_event_type ADD VALUE 'TOOTH_CHART_VIEWED';
ALTER TYPE audit_event_type ADD VALUE 'PATIENT_DATA_EXPORTED';
ALTER TYPE audit_event_type ADD VALUE 'PATIENT_DATA_ERASURE_REQUESTED';
ALTER TYPE audit_event_type ADD VALUE 'PATIENT_DATA_ERASURE_COMPLETED';

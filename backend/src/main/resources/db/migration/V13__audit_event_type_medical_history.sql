-- New audit_event_type values for feature 004-patient-medical-history (data-model.md
-- AuditLogEntry section, research.md #2). Generic + metadata discriminator: one ADDED/VIEWED
-- pair plus a distinct HISTORY_VIEWED for opening "historia zmian", shared across all three
-- sub-resources (allergies/medications/chronic-conditions) rather than nine specific event types
-- (research.md #2's rejected alternative) — metadataJson.entryType disambiguates which section.
ALTER TYPE audit_event_type ADD VALUE 'MEDICAL_HISTORY_ENTRY_ADDED';
ALTER TYPE audit_event_type ADD VALUE 'MEDICAL_HISTORY_ENTRY_VIEWED';
ALTER TYPE audit_event_type ADD VALUE 'MEDICAL_HISTORY_HISTORY_VIEWED';

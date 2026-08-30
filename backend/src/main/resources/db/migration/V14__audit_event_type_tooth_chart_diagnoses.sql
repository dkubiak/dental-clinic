-- New audit_event_type values for feature 005-tooth-chart-diagnoses (research.md D9). One
-- TOOTH_FINDING_ADDED value covers create, correct, AND close — all three are the same
-- supersede-then-insert operation (D3); before_state/after_state JSON distinguishes them, same
-- reasoning V13 already used for MEDICAL_HISTORY_ENTRY_ADDED. The existing TOOTH_CHART_VIEWED
-- value (002) is reused as-is; TOOTH_STATE_CHANGED (002) is left inert in the enum — Postgres
-- cannot cheaply drop a value from a native enum, and an unused label costs nothing.
ALTER TYPE audit_event_type ADD VALUE 'TOOTH_POSITION_PRESENCE_CHANGED';
ALTER TYPE audit_event_type ADD VALUE 'DENTITION_MODE_CHANGED';
ALTER TYPE audit_event_type ADD VALUE 'ROOT_CANAL_ADDED';
ALTER TYPE audit_event_type ADD VALUE 'ROOT_CANAL_CHANGED';
ALTER TYPE audit_event_type ADD VALUE 'ROOT_CANAL_REMOVED';
ALTER TYPE audit_event_type ADD VALUE 'TOOTH_FINDING_ADDED';

-- FR-006a (002-patient-records): a fourth staff role, ASSISTANT ("asystent/asystentka"),
-- scoped to read-only basic patient data (identification) + read/write tooth-chart access
-- (rbac-policy.md, updated by feature 002). Postgres requires an enum value addition to commit
-- in its own transaction before the value can be referenced by other migrations/code
-- (research.md #4) — hence this is its own, minimal migration, ordered before any later
-- migration or seed data that references 'ASSISTANT'.
ALTER TYPE staff_role ADD VALUE 'ASSISTANT';

# Phase 1 Data Model: Historia medyczna pacjenta

All three entities below live in `patient-service`'s own schema (new migration
`V3__medical_history.sql`, next after `V1__patient_record.sql` / `V2__tooth_state.sql`), package
`com.dentalclinic.patient.medicalhistory`. Field naming and FK style match the existing
`ToothState` (V2) precedent: no DB-level FK to `staff_account` (owned by `backend`'s separate
migration history — research.md #5 of 002), a real FK to `patient_record` (owned by this same
service's own history).

Shared concept across all three tables, per research.md #3 (Clarifications Session 2026-08-29 Q1):

- **`record_status`** (new Postgres enum `medical_history_record_status`: `CURRENT`,
  `SUPERSEDED`) — a technical correction-lifecycle flag, unrelated to any clinical-status field an
  entity may also carry (see `ChronicConditionEntry` below).
- **`supersedes_entry_id`** — nullable, self-referencing FK to the row this entry corrects. `NULL`
  for a first-time entry.

## AllergyEntry

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | `gen_random_uuid()` default |
| `patient_record_id` | `UUID NOT NULL FK → patient_record(id)` | |
| `substance` | `TEXT NOT NULL` | free text (FR-011, Clarifications — no dictionary validation) |
| `reaction_type` | `TEXT NOT NULL` | free text |
| `severity` | `allergy_severity NOT NULL` | new enum: `CRITICAL`, `MODERATE` |
| `record_status` | `medical_history_record_status NOT NULL DEFAULT 'CURRENT'` | |
| `supersedes_entry_id` | `UUID NULL FK → allergy_entry(id)` | |
| `created_at` | `TIMESTAMPTZ NOT NULL` | |
| `created_by` | `UUID NOT NULL` | `staff_account.id`, no DB-level FK (cross-service) |

Validation: `severity` MUST be one of the two enum values (FR-001). `substance`/`reaction_type`
MUST NOT be blank (standard non-empty validation, no dedicated FR — reasonable default).

State transitions: `CURRENT` → `SUPERSEDED`, one-way, only as the side effect of inserting a new
`CURRENT` entry with `supersedes_entry_id` pointing at this row (FR-010). No other transition
exists — no direct delete, no direct field edit after creation.

## MedicationEntry

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `patient_record_id` | `UUID NOT NULL FK → patient_record(id)` | |
| `name` | `TEXT NOT NULL` | free text (FR-011) |
| `dosage` | `TEXT NOT NULL` | free text — e.g. "500mg 2x/dzień"; no unit-of-measure model (out of scope, spec.md Assumptions) |
| `start_date` | `DATE NOT NULL` | |
| `record_status` | `medical_history_record_status NOT NULL DEFAULT 'CURRENT'` | |
| `supersedes_entry_id` | `UUID NULL FK → medication_entry(id)` | |
| `created_at` | `TIMESTAMPTZ NOT NULL` | |
| `created_by` | `UUID NOT NULL` | |

Same state-transition rule as `AllergyEntry`.

## ChronicConditionEntry

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `patient_record_id` | `UUID NOT NULL FK → patient_record(id)` | |
| `name` | `TEXT NOT NULL` | free text (FR-011) |
| `clinical_status` | `chronic_condition_status NOT NULL` | new enum: `ACTIVE`, `PAST` — the patient's actual health state; **independent of `record_status`** (Clarifications Session 2026-08-29 Q1) |
| `diagnosis_date` | `DATE NOT NULL` | |
| `record_status` | `medical_history_record_status NOT NULL DEFAULT 'CURRENT'` | correction-lifecycle flag only, see above |
| `supersedes_entry_id` | `UUID NULL FK → chronic_condition_entry(id)` | |
| `created_at` | `TIMESTAMPTZ NOT NULL` | |
| `created_by` | `UUID NOT NULL` | |

Two independent state machines on this one entity, per the clarification: `clinical_status`
(`ACTIVE` ⇄ `PAST`, a *new correction entry* is how a doctor would flip it, same append-only rule —
there is no in-place clinical-status toggle either) is orthogonal to `record_status` (`CURRENT` →
`SUPERSEDED`, one-way, correction-only).

## PatientRecord (existing entity, feature 002) — one new derived field

`PatientDetailResponse` (API layer only, no new column on `patient_record` itself — research.md
#5) gains:

| Field | Type | Notes |
|---|---|---|
| `hasCriticalAllergyAlert` | `boolean` | `EXISTS (SELECT 1 FROM allergy_entry WHERE patient_record_id = :id AND record_status = 'CURRENT' AND severity = 'CRITICAL')`, computed per request, no caching/denormalization |

## AuditLogEntry (existing shared table, feature 001) — no schema change, new enum values only

Three new `audit_event_type` values (`backend` migration `V13__audit_event_type_medical_history.sql`,
research.md #2): `MEDICAL_HISTORY_ENTRY_ADDED`, `MEDICAL_HISTORY_ENTRY_VIEWED`,
`MEDICAL_HISTORY_HISTORY_VIEWED`. Written exactly like `TOOTH_STATE_CHANGED`/`TOOTH_CHART_VIEWED`
via the existing `PatientAuditWriter` — same table, same hash chain, same advisory lock. Every row
these three types produce sets `metadata` to `{"entryType": "ALLERGY" | "MEDICATION" |
"CHRONIC_CONDITION"}` (research.md #2). For `MEDICAL_HISTORY_ENTRY_ADDED` on a correction
(`supersedesEntryId` present in the request), `before_state` is the superseded row's snapshot and
`after_state` is the new row's; for a first-time entry, `before_state` is `null`.

## Relationships

```
patient_record (002)
  ├── tooth_state (002, V2)               [unchanged]
  ├── allergy_entry (this feature, V3)    patient_record_id FK, self-referencing supersedes_entry_id
  ├── medication_entry (this feature, V3) patient_record_id FK, self-referencing supersedes_entry_id
  └── chronic_condition_entry (this feature, V3)
                                           patient_record_id FK, self-referencing supersedes_entry_id

audit_log_entry (001, shared, unchanged schema)
  └── target_patient_record_id FK-by-convention (no DB FK, research.md #5 of 002)
      — this feature's 3 new event types write here exactly like 002's tooth-chart events do
```

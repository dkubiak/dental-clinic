# Phase 1 Data Model: Kartoteka pacjentów

Derived from spec.md Key Entities, expanded with the fields implied by the Functional
Requirements and the technical decisions in research.md. New tables (`patient_record`,
`tooth_state`) are owned by the new, independent `patient-service` deployable (plan.md "Risk Tier
& Availability"), but live in the same Postgres instance `auth-service` already provisions — no
new datastore, and no DB-level foreign keys cross the service boundary (research.md #5/#7).

## Entities

### PatientRecord

Represents a patient's kartoteka. Corresponds to spec's "Pacjent (kartoteka)".

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `first_name` | text, not null | FR-001. |
| `last_name` | text, not null | FR-001; indexed for search (FR-012). |
| `date_of_birth` | date, not null | Added beyond the original basic-data list (spec.md Assumptions) — needed to distinguish same-name patients once PESEL is optional. |
| `pesel` | char(11), nullable | FR-002; format+checksum validated server-side when present (research.md #1). Unique when not null (see index below). |
| `address_street` | text, not null | FR-001; structured address (research.md — Address representation). |
| `address_building_no` | text, not null | |
| `address_postal_code` | text, not null | |
| `address_city` | text, not null | |
| `created_at` | timestamptz, not null | |
| `created_by` | UUID, **no DB-level FK**, not null | The rejestrator/lekarz who created the record (FR-001) — id read from the shared session (research.md #7); `staff_account` is owned by the separate `auth-service`, so no cross-service FK (research.md #5). |
| `updated_at` | timestamptz, not null | |
| `updated_by` | UUID, **no DB-level FK**, nullable | Last editor of basic data (FR-011); same cross-service caveat as `created_by`. |

**Indexes**: unique partial index on `pesel WHERE pesel IS NOT NULL` (FR-003, research.md #2);
btree index on `last_name` (FR-012, case-insensitive search).

**Validation rules**:
- `pesel`, when present, must be 11 digits and pass the standard Polish checksum (research.md #1);
  reject the write otherwise (US1 Acceptance Scenario 3).
- Creating a record with a `pesel` that already exists is rejected (US1 Acceptance Scenario 4).
- A record with no `pesel` is created without any duplicate check (US1 Acceptance Scenario 5,
  accepted risk — spec.md Edge Cases).

**State transitions**: none beyond create/edit of basic-data fields (FR-011); no soft-delete/
status field in this version — RODO erasure (FR-010) is handled as its own workflow (see
`patient-api.yaml`), not a `status` flag on this table.

### ToothState

One row per tooth per patient, pre-created at PatientRecord creation time (research.md #3).
Corresponds to spec's "Stan uzębienia".

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `patient_record_id` | UUID, FK → patient_record.id, not null | |
| `tooth_number` | smallint, not null | FDI/ISO 3950 notation, one of the 32 adult permanent-dentition values (11–18, 21–28, 31–38, 41–48) — FR-005. |
| `status` | enum: `HEALTHY` \| `SICK`, not null, default `HEALTHY` | FR-006; binary only in this version (spec.md Assumptions — colors/descriptions/disease codes deferred). |
| `updated_at` | timestamptz, not null | |
| `updated_by` | UUID, **no DB-level FK**, nullable | The `DOCTOR`/`ASSISTANT` who last changed this tooth's state; null immediately after auto-creation (never edited yet). Same cross-service FK caveat as `PatientRecord.created_by` (research.md #5). |

**Indexes**: unique index on `(patient_record_id, tooth_number)` (exactly one row per tooth per
patient); index on `patient_record_id` (chart lookup by patient).

**Validation rules**:
- Exactly 32 rows are created per PatientRecord, one per valid FDI tooth number, at record-creation
  time (US2 Acceptance Scenario 3 — "domyślnie zdrowy").
- Only `RECEPTION`-excluded roles (`DOCTOR`, `ASSISTANT`) may write `status`; enforced server-side
  via `@PreAuthorize`, same pattern as 001 (US2 Acceptance Scenario 4).

**State transitions**: `HEALTHY ⇄ SICK`, freely bidirectional, always audit-logged (FR-007,
research.md #5) — no other states exist in this version.

### AuditLogEntry (extension of 001's table, owned by `auth-service`)

No new table — 001's `audit_log_entry` (`V5__audit_log.sql`, in `backend/`/`auth-service`) is
extended in place (research.md #5), preserving its single hash chain. The `INSERT`/`SELECT`-only
grant boundary is extended to the new `patient_service_app` role (below), not loosened.

**New `audit_event_type` enum values**:
- `PATIENT_RECORD_CREATED`
- `PATIENT_RECORD_UPDATED`
- `PATIENT_RECORD_VIEWED` (FR-007/SC-003 — read access to basic data: `GET /patients/{id}` and
  `GET /patients?q=`; one entry per search *call*, not per matched record, with the query and hit
  count captured in `metadata`)
- `TOOTH_STATE_CHANGED`
- `TOOTH_CHART_VIEWED` (FR-007/SC-003 — read access to the tooth chart: `GET
  /patients/{id}/tooth-chart`)
- `PATIENT_DATA_EXPORTED` (FR-009)
- `PATIENT_DATA_ERASURE_REQUESTED` / `PATIENT_DATA_ERASURE_COMPLETED` (FR-010)

**New column**:

| Field | Type | Notes |
|---|---|---|
| `target_patient_record_id` | UUID, **no DB-level FK**, nullable | Populated for all patient-scoped event types above; `target_account_id` (001) remains populated only for staff-account-scoped events. Exactly one of the two target columns is non-null per row for any event introduced by this feature. Deliberately **not** a foreign key to `patient_record.id` (owned by `patient-service`'s own migration history) — a cross-service DB constraint would couple the two services' schema-migration ordering; referential integrity for this column is enforced at the application layer only (research.md #5, revised after the services were split). |

**Concurrency fix required by this feature** (research.md #5a): `AuditLogWriter`'s in-process
`synchronized` block is replaced with `pg_advisory_xact_lock(<fixed key>)` around "read tail →
compute hash → insert", so the chain stays correct across both `auth-service`'s own replicas and
`patient-service`'s writes.

**`patient_service_app` DB role** (created idempotently by whichever of `patient-service`'s or
`auth-service`'s migrations runs first — research.md #7):

| Grant | Scope | Notes |
|---|---|---|
| `SELECT, INSERT, UPDATE, DELETE` | `patient_record`, `tooth_state` | Tables `patient-service` owns outright. Both PKs are UUID (`gen_random_uuid()` default), so **no sequence grant applies** here — unlike the `audit_log_entry` row below. |
| `SELECT, UPDATE` | `spring_session`, `spring_session_attributes` | Read-only session validation + `last_access_time` bump; never `INSERT`/`DELETE` — session lifecycle stays `auth-service`'s responsibility. |
| `SELECT, INSERT` | `audit_log_entry` (+ its sequence) | Same tamper-evidence boundary as `auth_service_app` (001) — `UPDATE`/`DELETE` explicitly revoked/never granted. |

### Role (extension of 001's `staff_role` enum, owned by `auth-service`)

No new table — `Role.java` and the `staff_role` Postgres enum (both in `backend/`/`auth-service`)
gain a fourth value, `ASSISTANT` (research.md #4), added via
`ALTER TYPE staff_role ADD VALUE 'ASSISTANT'` in its own `auth-service` migration (Postgres
requires enum-value additions to be committed before the value can be used, so this migration
must precede any migration/seed data referencing it). `patient-service` never writes to
`staff_account`/`staff_role` directly — it only reads the role out of the deserialized session
attribute (research.md #7), so this remains a single-owner (`auth-service`) schema object despite
being consumed by two services.

**Permission delta** (full matrix lives in `rbac-policy.md`, updated by this feature):

| Resource / action | RECEPTION | DOCTOR | ASSISTANT | ADMINISTRATOR |
|---|---|---|---|---|
| Create / edit patient basic data | ✅ | ✅ | ❌ | ❌ |
| Read patient basic data (identification only) | ✅ | ✅ | ✅ | ❌ |
| View / edit tooth chart | ❌ | ✅ | ✅ | ❌ |
| View visit-history placeholder | ✅ | ✅ | ❌ | ❌ |
| Export / erase patient data (RODO, FR-009/FR-010) | ❌ | ✅ | ❌ | ❌ |

## Entity relationship summary

```text
                    auth-service (owns)              patient-service (owns)
                    ───────────────────              ──────────────────────
                    staff_account, staff_role         patient_record, tooth_state
                    spring_session*, audit_log_entry

staff_account (app-level id, no cross-service FK) ──* patient_record   (created_by / updated_by)
patient_record                                     1─32 tooth_state    (fixed set, FDI 11–48)
staff_account (app-level id, no cross-service FK) ──* tooth_state      (updated_by)
patient_record (app-level id, no DB FK)            ─* audit_log_entry  (target_patient_record_id,
                                                                         see AuditLogEntry section)
```

`patient_record`/`tooth_state`'s `created_by`/`updated_by` columns store a `staff_account.id`
value but likewise carry **no DB-level FK** across the service boundary (same reasoning as
`audit_log_entry.target_patient_record_id`) — `patient-service` trusts the id it reads out of the
shared session, it does not join against `auth-service`'s table.

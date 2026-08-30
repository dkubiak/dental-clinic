# Phase 1 Data Model: Interaktywny odontogram z rozpoznaniami i powierzchniami zębów

All entities below live in `patient-service`'s own schema, migration
`V4__tooth_chart_diagnoses.sql` (next after `V1__patient_record.sql` / `V2__tooth_state.sql` /
`V3__medical_history.sql` — research.md D1), package `com.dentalclinic.patient.toothchart`. This
migration **drops** `tooth_state` and the `tooth_status` enum (research.md D1 — no migration path,
no production data exists). Field naming/FK style continues the existing precedent: no DB-level FK
to `staff_account` (owned by `backend`'s separate migration history), real FKs within this
service's own history.

## ToothChart

One row per `patient_record` — the aggregate root FR-005's Key Entities section describes as "nie
przechowuje stanu zęba wprost" (research.md D2).

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `patient_record_id` | `UUID NOT NULL UNIQUE FK → patient_record(id)` | one chart per patient |
| `dentition_mode` | `dentition_mode NOT NULL DEFAULT 'PERMANENT'` | new enum: `PERMANENT`, `DECIDUOUS`, `MIXED` (FR-043) |
| `dentition_mode_set_by` | `UUID NULL` | `staff_account.id`; `NULL` if still the age-derived default (FR-044) |
| `dentition_mode_set_at` | `TIMESTAMPTZ NULL` | |

Created by `ToothChartInitializer` at patient-record creation, together with all 52 `tooth_position`
rows below (research.md D2). `dentition_mode` defaults from age at creation time (FR-044) and can be
explicitly overridden later (FR-045) — overriding never touches `tooth_position`/`tooth_finding`
rows (FR-047).

## ToothPosition

52 rows per chart, created once and never deleted: FDI 11-18/21-28/31-38/41-48 (32 permanent) +
51-55/61-65/71-75/81-85 (20 deciduous).

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `tooth_chart_id` | `UUID NOT NULL FK → tooth_chart(id)` | |
| `fdi_number` | `SMALLINT NOT NULL` | 11-85 range per FDI/ISO 3950 (FR-003); `UNIQUE (tooth_chart_id, fdi_number)` |
| `dentition_type` | `dentition_type NOT NULL` | new enum: `PERMANENT`, `DECIDUOUS` — fixed at row creation, tells the frontend which positions to show for a given `dentition_mode` (`MIXED` shows both) |
| `tooth_type` | `tooth_type NOT NULL` | new enum: `INCISOR`, `CANINE`, `PREMOLAR`, `MOLAR` — derived once from `fdi_number` at seed time, drives crown cusp-count/root-count parameters (research.md D11) and which surfaces exist (FR-024) |
| `presence` | `tooth_presence NOT NULL DEFAULT 'PRESENT'` | new enum: `PRESENT`, `EXTRACTED`, `CONGENITALLY_MISSING`, `UNERUPTED` (FR-038) |
| `presence_date` | `DATE NULL` | e.g. extraction date, when known (FR-038) |
| `version` | `INTEGER NOT NULL DEFAULT 0` | JPA `@Version` — optimistic concurrency (research.md D7) |
| `updated_at` | `TIMESTAMPTZ NOT NULL` | |
| `updated_by` | `UUID NULL` | `staff_account.id` |

Mutated in place (not append-only, research.md D4) — only `presence`/`presence_date` change after
creation, each change audit-logged with before/after snapshot exactly like the old
`ToothChartService.setStatus(...)`. Validation: `presence != PRESENT` blocks new `ToothFinding`
rows with `anatomical_scope = SURFACE` on this position (FR-040); `presence = PRESENT` is required
for `RootCanal` rows to be added (a canal implies a physically present tooth).

## RootCanal

Zero or more per `ToothPosition` (up to 6, FR-065) — a mutable, non-append-only entity
(research.md D4).

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `tooth_position_id` | `UUID NOT NULL FK → tooth_position(id)` | |
| `name` | `TEXT NOT NULL` | anatomical name, free text (e.g. "policzkowy bliższy", "MB2" — FR-065) |
| `state` | `root_canal_state NOT NULL DEFAULT 'NEEDS_TREATMENT'` | new enum: `NEEDS_TREATMENT`, `TREATED`, `UNDERTREATED` (FR-066) |
| `removed` | `BOOLEAN NOT NULL DEFAULT FALSE` | soft delete only (FR-068) |
| `version` | `INTEGER NOT NULL DEFAULT 0` | JPA `@Version` (research.md D7) |
| `created_at` / `created_by` | `TIMESTAMPTZ NOT NULL` / `UUID NOT NULL` | |
| `updated_at` / `updated_by` | `TIMESTAMPTZ NOT NULL` / `UUID NULL` | last state/name change |

Validation: max 6 non-removed canals per `tooth_position` (FR-065). Removing a canal (`removed =
true`) never cascades to `tooth_finding` rows referencing it (FR-068) — the frontend flags such
findings as "kanał nieobecny w bieżącym modelu" by checking `root_canal.removed`.

## DiagnosisCatalogEntry

Flyway-seeded reference data (research.md D5), read-only via the app. Never updated/deleted by
migrations after a version ships — only added to (FR-019).

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `code` | `TEXT NOT NULL UNIQUE` | stable technical code (FR-012), e.g. `CARIES_DENTIN` |
| `name_pl` | `TEXT NOT NULL` | Polish display name (FR-012) |
| `category` | `diagnosis_category NOT NULL` | new enum, the seven FR-014 groups |
| `anatomical_scope` | `anatomical_scope NOT NULL` | new enum: `SURFACE`, `WHOLE_TOOTH`, `ROOT_PERIAPICAL`, `PERIODONTIUM` (FR-021) |
| `layer` | `finding_layer NOT NULL` | new enum: `DIAGNOSIS`, `EXISTING_STATE` (FR-016) |
| `icd10_code` | `TEXT NULL` | where an equivalent exists (FR-012) |
| `severity_options` | `TEXT[] NULL` | closed list for entries that need one, e.g. `{SZKLIWA,ZEBINY,GLEBOKA}` (FR-018); `NULL` when not applicable |
| `allowed_for_missing_tooth` | `BOOLEAN NOT NULL DEFAULT FALSE` | true only for entries valid on an `EXTRACTED`/`CONGENITALLY_MISSING` position (implant, przęsło mostu, stan po ekstrakcji — FR-041) |
| `deciduous_allowed` | `BOOLEAN NOT NULL DEFAULT TRUE` | false for adult-only entries where relevant |
| `quick_access` | `BOOLEAN NOT NULL DEFAULT FALSE` | drives the context menu's "najczęstsze" section (research.md D12) |
| `requires_free_text` | `BOOLEAN NOT NULL DEFAULT FALSE` | true for exactly four "inne rozpoznanie" fallback rows, one per `anatomical_scope` value (FR-011a, session 2026-08-30 piąta tura) |
| `catalog_version` | `INTEGER NOT NULL` | the migration-set number this row shipped in (FR-019 — never reused/renumbered) |

Validation: `severity_options` present only when the FR-018 examples call for it; `anatomical_scope
= SURFACE` is the only scope that requires a client-supplied surface set on the resulting
`ToothFinding` (FR-022/FR-023).

**"Inne rozpoznanie" is four seeded rows, not one** (session 2026-08-30 piąta tura, resolving a
`/speckit-analyze` finding): `anatomical_scope` is a fixed column on the catalog *entry*, so a
single fallback row cannot carry a user-chosen scope. Instead the seed contains one
`requires_free_text = true` row per `AnatomicalScope` value (`SURFACE`, `WHOLE_TOOTH`,
`ROOT_PERIAPICAL`, `PERIODONTIUM`) — the user picks whichever "inne rozpoznanie" row matches the
intended scope from catalog search, keeping "scope always follows the referenced catalog entry"
true without exception. No `ToothFindingCreateRequest` field or `ToothFindingService` special case
is needed for this.

## ToothFinding

The append-only clinical entry (research.md D3) — one row per observation, immutable except via
the supersede operation described below.

| Column | Type | Notes |
|---|---|---|
| `id` | `UUID PK` | |
| `tooth_position_id` | `UUID NOT NULL FK → tooth_position(id)` | |
| `diagnosis_catalog_entry_id` | `UUID NOT NULL FK → diagnosis_catalog_entry(id)` | |
| `surfaces` | `tooth_surface[] NULL` | new enum array: `MESIAL, DISTAL, VESTIBULAR, LINGUAL_PALATAL, OCCLUSAL_INCISAL` (FR-024); required (`NOT NULL`, min 1) iff the catalog entry's `anatomical_scope = SURFACE` (FR-022), forbidden otherwise (FR-023) — enforced in `ToothFindingService`, not a DB constraint, since it depends on a join |
| `root_canal_id` | `UUID NULL FK → root_canal(id)` | only set when `anatomical_scope = ROOT_PERIAPICAL` and a specific canal is indicated (FR-028, FR-067); `NULL` = "whole root", never required |
| `severity` | `TEXT NULL` | one of the referenced catalog entry's `severity_options` (FR-018) |
| `free_text_description` | `TEXT NULL` | required iff `diagnosis_catalog_entry.requires_free_text` (FR-011a) |
| `note` | `VARCHAR(1000) NULL` | optional clinician note (FR-017) |
| `diagnosis_date` | `DATE NOT NULL` | may be in the past, MUST NOT be future or before the patient's `date_of_birth` (FR-036) |
| `resolved_date` | `DATE NULL` | set only when `clinical_status = RESOLVED` (FR-032) |
| `clinical_status` | `finding_clinical_status NOT NULL DEFAULT 'ACTIVE'` | new enum: `ACTIVE`, `RESOLVED` — independent of `record_status` below (research.md D3, mirrors 004's `ChronicConditionEntry.clinical_status`) |
| `record_status` | `finding_record_status NOT NULL DEFAULT 'CURRENT'` | new enum: `CURRENT`, `SUPERSEDED` — technical correction-lifecycle flag, unrelated to `clinical_status` |
| `supersedes_finding_id` | `UUID NULL FK → tooth_finding(id)`, **`UNIQUE` where not null** | the finding this one corrects/closes; the partial unique index is what makes a concurrent double-correction fail at the DB level (research.md D7) |
| `author_account_id` | `UUID NOT NULL` | `staff_account.id`, no DB-level FK (cross-service) |
| `author_role` | `finding_author_role NOT NULL` | new enum: `DOCTOR`, `ASSISTANT` — snapshot of the role the author acted in *at write time* (FR-058), independent of that account's role today |
| `created_at` | `TIMESTAMPTZ NOT NULL` | |

Validation (`ToothFindingService`, needs the joined catalog entry so isn't a bare DB constraint):
`surfaces` required/forbidden per `anatomical_scope` (above); `tooth_position.presence != PRESENT`
blocks a new `SURFACE`-scope finding (FR-040) but not `allowed_for_missing_tooth` entries (FR-041);
`root_canal_id`, if set, must reference a non-`removed` canal on the same `tooth_position` at write
time (FR-067) — a later canal removal does not retroactively invalidate the reference (FR-068).

State transitions: exactly the two independent axes described in research.md D3 —

- `record_status`: `CURRENT` → `SUPERSEDED`, one-way, only as the side effect of inserting a new
  `CURRENT` row with `supersedes_finding_id` pointing at this one (FR-033). No direct delete, no
  direct field edit after creation (FR-030).
- `clinical_status`: `ACTIVE` → `RESOLVED` happens *only* via the same supersede operation (a
  "close" is a correction whose only semantic change is `clinical_status`/`resolved_date` —
  research.md D3) — there is no in-place toggle either.

## Relationships

```
patient_record (002)
  └── tooth_chart (this feature, V4)                 patient_record_id FK, 1:1
        └── tooth_position (this feature, V4)         tooth_chart_id FK, 52 rows, never deleted
              ├── root_canal (this feature, V4)        tooth_position_id FK, mutable, soft-delete only
              └── tooth_finding (this feature, V4)     tooth_position_id FK
                    ├── diagnosis_catalog_entry_id FK  (reference data, this feature, V4, seeded)
                    ├── root_canal_id FK (nullable)     must be non-removed at write time
                    └── supersedes_finding_id FK        self-referencing, UNIQUE where not null

audit_log_entry (001, shared, unchanged schema)
  └── target_patient_record_id FK-by-convention (no DB FK)
      — six new event types (research.md D9) write here exactly like every other patient-service
        write already does; TOOTH_CHART_VIEWED is reused as-is; TOOTH_STATE_CHANGED (002) becomes
        unused going forward but is not removed from the shared enum (research.md D9)
```

## PatientExport (RODO) — extended, no new mechanism

`PatientExportService`'s `PatientExport` record (research.md, existing pattern from 004's own
extension of the same class) gains one field:

| Field | Type | Notes |
|---|---|---|
| `toothChart` | `ToothChartExport` (chart + all 52 positions + canals + **full** `tooth_finding` history, current and superseded alike) | replaces the old `List<ToothState>` field; DOCTOR-only endpoint, unchanged (`rbac-policy.md` rule 6) |

`PatientFullExport`'s existing `toothChart` field in the OpenAPI contract is replaced in place
(contracts/README.md) rather than renamed, since it already occupies the correct name/position in
the schema — only its shape changes.

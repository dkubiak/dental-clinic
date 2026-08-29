# Implementation Plan: Historia medyczna pacjenta

**Branch**: `004-patient-medical-history` | **Date**: 2026-08-29 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/004-patient-medical-history/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Extend the existing patient kartoteka (feature 002) with a "Historia medyczna" section covering
three clinical sub-resources — allergies, medications, chronic/past conditions — as three new
tables inside `patient-service`'s own schema, exposed as new sub-resource endpoints under
`/patients/{patientId}/...` and rendered as a new tab on `patient-detail`. Reuses every mechanism
002 already built for the sibling `tooth-chart` sub-resource: the same `@PreAuthorize`/deny-404
RBAC pattern, the same shared hash-chained `audit_log_entry` table via `PatientAuditWriter`, the
same mobile-first Angular Material tab shell. The one genuinely new piece of design is the
append-only correction model (`recordStatus: CURRENT/SUPERSEDED` + `supersedesEntryId`) that FR-010
requires, since no existing sub-resource needed a correction history before. See `research.md` for
the eight architectural decisions this plan is built on.

## Technical Context

**Language/Version**: Java 25 (`patient-service`, unchanged) + TypeScript / Angular 21 (`frontend`,
unchanged) — no new language or runtime introduced.

**Primary Dependencies**: Spring Boot 4.1, Spring Data JPA, Flyway, Spring Security method security
(`@PreAuthorize`) on the backend; Angular Material 21, RxJS on the frontend — all already in use by
`patient-service`/`frontend` for the `tooth-chart` sub-resource this feature mirrors. No new
dependency is added.

**Storage**: PostgreSQL (AWS RDS/Aurora, unchanged hosting per Technology Stack Constraints). Three
new tables in `patient-service`'s own Flyway history (`V3__medical_history.sql`); three new values
appended to the existing, shared `audit_event_type` enum in `backend`'s Flyway history
(`V13__audit_event_type_medical_history.sql`). No new database, schema, or audit table.

**Testing**: JUnit 5 + Testcontainers for `patient-service` (new entities/services/controllers,
RBAC-denial cases, audit-log assertions — mirrors existing `ToothChartService`/`ToothChartController`
test style); Vitest for `frontend` (new component logic, tab-visibility guard, form validation,
service methods). Both are **live** CI jobs (`patient-service`, `frontend-unit` — CLAUDE.md). Any
Playwright e2e coverage for this feature is **not** CI-gated: it needs a running backend, and
`frontend-e2e` stays disabled (`if: false`) for the same reason 002's own Playwright specs aren't
gated — this feature is not reachable pre-auth like 003's theme toggle, so it cannot ride the
separate `frontend-e2e-theme` job either. Anything that must actually be enforced belongs in the
JUnit/Vitest suites, not a Playwright-only check.

**Known coverage limitation (accepted, not a defect)**: SC-004's "no-scroll" claim is verified
manually only, via `quickstart.md` Scenario 1 step 2. Vitest (`medical-history.component.spec.ts`,
`patient-detail.component.spec.ts`) runs under jsdom and can assert DOM structure/order for the
critical-allergy badge, but not real browser layout/scroll position. This feature deliberately does
not add a new Playwright CI job the way `003-brand-ui-theme` added `frontend-e2e-theme` for its own
layout-dependent checks (CLAUDE.md, Theming), because this feature's UI is not reachable pre-auth
the way the theme toggle is — it would need a running backend, which `frontend-e2e` (disabled,
`if: false`) does not yet provision (see Testing above). Revisit if a future feature provisions
Postgres/LocalStack for `frontend-e2e` and makes an authenticated Playwright job feasible in CI.

**Target Platform**: Amazon EKS (unchanged) — this feature adds no new Helm release/Deployment; it
ships inside the existing `patient-service` chart via the existing pipeline.

**Project Type**: Web application (existing `backend/` + `patient-service/` + `frontend/` layout,
unchanged — Option 2 shape already established by 001/002).

**Performance Goals**: No feature-specific target in spec.md; inherits `patient-service`'s existing,
undocumented-but-unremarkable request-latency norms (same order of magnitude as the `tooth-chart`
endpoints it mirrors — small per-patient row counts, no batch/bulk operations).

**Constraints**: RODO Art. 9 special-category data (spec.md, constitution Principle II) — encrypted
at rest/in transit via existing RDS/TLS infrastructure (no new encryption mechanism needed);
append-only correction model is a hard constraint from FR-010 (no `UPDATE`/`DELETE` endpoint may
ever be added to these three resources); every read and write must reuse the single existing
hash-chained `audit_log_entry` table (Principle III) — no second, parallel audit mechanism.

**Scale/Scope**: Same patient population as `patient-service` already serves (spec.md carries no
new scale requirement). Three new per-patient collections, unbounded length per patient in
principle but bounded in practice by realistic clinical-history size (tens of entries, not
thousands) — no pagination requirement identified; revisit if quickstart.md Scenario 2/3 load
testing later shows otherwise.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — no changes required;
the design that emerged from Phase 0/1 satisfies every gate below exactly as anticipated.*

| Principle / Gate | Status | How this plan satisfies it |
|---|---|---|
| I. Test-First Development | PASS | `/speckit-tasks` must sequence a failing JUnit test (entity/service/controller/RBAC-denial) and a failing Vitest test (component/service) before each corresponding implementation task — same Red-Green-Refactor discipline as 001/002/003. |
| II. Patient Data Protection & RODO (NON-NEGOTIABLE) | PASS | Special-category data (Art. 9) inherits existing at-rest/in-transit encryption (RDS/TLS, no new mechanism). RBAC scoped by job function per `rbac-policy.md` rule 7 (research.md #5). Export extended (`FR-009`, research.md #6); erasure's existing, already-reviewed deferral (`TODO(T060)`) covers the new tables by construction, not reopened here. |
| III. Full Auditability | PASS | Every read and write goes through the existing, single hash-chained `audit_log_entry` table via `PatientAuditWriter` — no new audit table, no editable/deletable path (data-model.md, research.md #2). |
| IV. Mobile-First Design | PASS | New tab follows the same Angular Material mobile-first tab pattern already used for `tooth-chart`/`visit-history` on `patient-detail.component.ts` — no new layout paradigm. |
| V. Risk-Tiered High Availability | PASS | Stays inside `patient-service`'s existing high-risk-tier failure domain (no new deployable, no new Helm release) — research.md #1. Module boundary: unchanged from 002's own plan.md, documented here rather than restated. |
| VI. Infrastructure & Delivery as Code (NON-NEGOTIABLE) | PASS | No new Terraform/Helm/Argo CD resource — Flyway migrations ship inside the existing `patient-service`/`backend` deployables through the existing GitHub Actions pipeline. |
| Environments & Release Process | PASS | Rides `patient-service`'s existing canary progressive-delivery configuration — no new rollout object needed. |
| Development Workflow & Quality Gates | ACTION REQUIRED AT PR TIME | This change touches patient data and audit logging → the PR MUST carry a documented security/compliance self-review before merge, and auto-merge MUST NOT be enabled for it (constitution v1.5.0 risk-tiered rule) — flagged here so `/speckit-implement`'s final PR does not default to the green-CI-only path that non-sensitive changes get. |

No entries required in Complexity Tracking — no principle is being deviated from or traded off.

## Project Structure

### Documentation (this feature)

```text
specs/004-patient-medical-history/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output — pointer file; the actual contracts are amended in
│                          # place in specs/001-staff-auth-rbac/ and specs/002-patient-records/
│                          # (research.md #8)
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Existing web-application layout (established by 001/002, unchanged shape) — this feature only adds
files inside the existing `patient-service/` and `frontend/` trees, plus one migration inside
`backend/`:

```text
backend/                                            # auth-service — unchanged except:
└── src/main/resources/db/migration/
    └── V13__audit_event_type_medical_history.sql    # NEW — 3 audit_event_type enum values

patient-service/
├── src/main/java/com/dentalclinic/patient/
│   ├── medicalhistory/                              # NEW package, mirrors toothchart/ shape
│   │   ├── AllergyEntry.java
│   │   ├── AllergyEntryRepository.java
│   │   ├── MedicationEntry.java
│   │   ├── MedicationEntryRepository.java
│   │   ├── ChronicConditionEntry.java
│   │   ├── ChronicConditionEntryRepository.java
│   │   ├── RecordStatus.java
│   │   └── MedicalHistoryService.java                # add/read/history for all 3 entity types
│   ├── api/
│   │   ├── MedicalHistoryController.java              # NEW — 6 endpoints (data-model.md)
│   │   ├── AllergyEntryResponse.java (+ Medication/ChronicCondition variants, request records)
│   │   └── PatientDetailResponse.java                 # MODIFIED — + hasCriticalAllergyAlert
│   ├── audit/PatientAuditEventType.java                # MODIFIED — + 3 new values
│   └── rodo/PatientExportService.java                  # MODIFIED — + allergies/medications/chronicConditions
├── src/main/resources/db/migration/
│   └── V3__medical_history.sql                         # NEW — 3 tables + 2 enums
└── src/test/java/com/dentalclinic/patient/
    ├── medicalhistory/ (unit tests, mirrors toothchart/ test package)
    └── api/MedicalHistoryControllerTest.java (RBAC + audit assertions, mirrors ToothChartControllerTest)

frontend/src/app/
├── features/patients/
│   ├── medical-history/                                # NEW, mirrors tooth-chart/ shape
│   │   ├── medical-history.component.ts
│   │   ├── medical-history.component.spec.ts
│   │   └── medical-history.service.ts
│   ├── patient-detail/patient-detail.component.ts       # MODIFIED — + tab, + critical-alert badge
│   └── patients.models.ts                               # MODIFIED — + new entity/enum types, + hasCriticalAllergyAlert
```

**Structure Decision**: Web application, Option 2 shape (already established by 001/002) —
`backend/` (auth-service) + `patient-service/` + `frontend/`. This feature adds one migration to
`backend/`, one new package + one new migration + several modified files to `patient-service/`,
and one new component + several modified files to `frontend/`. No new deployable, no new top-level
directory, no change to the existing three-service topology.

## Complexity Tracking

Not applicable — the Constitution Check above has no violations to justify.

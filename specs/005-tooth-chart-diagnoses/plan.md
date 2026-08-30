# Implementation Plan: Interaktywny odontogram z rozpoznaniami i powierzchniami zębów

**Branch**: `claude/teeth-visualization-model-6fhj1g` (katalog funkcji: `005-tooth-chart-diagnoses`) | **Date**: 2026-08-30 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/005-tooth-chart-diagnoses/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Replace `002-patient-records`'s minimal binary tooth chart (32 teeth, `HEALTHY`/`SICK`) with a full
clinical odontogram: a versioned, Flyway-seeded diagnosis catalog; per-position presence, surfaces,
and manually-tracked root canals; append-only clinical findings with the same
correct-and-close-are-both-supersede pattern `004-patient-medical-history` established; multi-select
bulk entry; deciduous/mixed dentition; and a mostly-rewritten frontend that ports the
already-user-approved mockup's procedural per-tooth-type SVG rendering rather than reinventing
layout from the FRs alone. `ToothState`/`ToothStatus` (002) are dropped outright — no production
data exists to migrate (spec.md Assumptions). Thirteen architectural decisions this plan rests on
are in `research.md`; the mockup (`mockup/odontogram-mockup.html`) is a visual reference the plan
follows closely except where it improvises styling outside `003-brand-ui-theme`'s token system
(flagged as seven new tokens, research.md D10) — not a contract (spec.md, "Mockup UI" section).

## Technical Context

**Language/Version**: Java 25 (`patient-service`, unchanged) + TypeScript / Angular 21 (`frontend`,
unchanged) — no new language or runtime introduced.

**Primary Dependencies**: Spring Boot 4.1, Spring Data JPA (incl. `@Version` optimistic locking,
newly used by this feature — research.md D7), Flyway, Spring Security method security
(`@PreAuthorize`) on the backend; Angular Material 21, RxJS on the frontend — all already in use by
`patient-service`/`frontend`. No new dependency is added; inline SVG generation (research.md D11)
uses plain TypeScript, not a charting/graphics library.

**Storage**: PostgreSQL (AWS RDS/Aurora, unchanged hosting). One new migration in
`patient-service` (`V4__tooth_chart_diagnoses.sql`) that **drops** `tooth_state`/`tooth_status`
(002) and creates `tooth_chart`, `tooth_position`, `root_canal`, `diagnosis_catalog_entry`
(seeded), `tooth_finding`, and their enums (data-model.md). One new migration in `backend`
(`V14__audit_event_type_tooth_chart_diagnoses.sql`) adding six values to the shared
`audit_event_type` enum (research.md D9). No new database or audit table.

**Testing**: JUnit 5 + Testcontainers for `patient-service` (new entities/services/controllers,
RBAC-denial cases, optimistic-lock 409s, correction/close-via-supersede assertions, bulk-save
skip-reporting, audit-log assertions — mirrors `ToothChartControllerTest`/`MedicalHistoryService`
test style); Vitest for `frontend` (tooth-geometry pure-function tests, selection-state/keyboard-nav
logic, context-menu quick-action wiring, catalog search filtering, and the existing
`contrast-audit.spec.ts`/`token-parity.spec.ts` extended to the seven new tokens). Both are **live**
CI jobs (`patient-service`, `frontend-unit` — CLAUDE.md). Playwright e2e is **not** CI-gated for
this feature, same reasoning 004 documented: `frontend-e2e` stays disabled (`if: false`), this
module needs a running authenticated backend so it can't ride the pre-auth-only
`frontend-e2e-theme` job either.

**Known coverage limitations (accepted, not defects)**: SC-004 (320px no-scroll) and SC-011
(<0.1s local selection feedback) are asserted structurally/via fake timers in Vitest, not measured
in a real browser — same accepted gap 004 documented for its own SC-004. SC-009 (5-doctor legend
comprehension study) is inherently a manual/human-subjects check, out of any automated suite by
definition; `quickstart.md` Scenario 8 documents it as a manual validation step. SC-001, SC-012,
and SC-013 (interaction-count/time-to-complete benchmarks) join this same accepted-gap category
(session 2026-08-30 piąta tura, resolving a `/speckit-analyze` finding): Vitest/jsdom cannot
reliably measure wall-clock time or a "real" interaction count, only method-call counts, so these
three are manual/structural validation, not a dedicated automated test, exactly like SC-004/
SC-009/SC-011 above.

**Target Platform**: Amazon EKS (unchanged) — this feature adds no new Helm release/Deployment; it
ships inside the existing `patient-service` and `frontend` charts via the existing pipeline.

**Project Type**: Web application (existing `backend/` + `patient-service/` + `frontend/` layout,
unchanged — Option 2 shape already established by 001/002).

**Performance Goals**: SC-011's <0.1s local selection-feedback target is a pure client-side
signal-update requirement (research.md D11 keeps selection state in an Angular signal, no
round-trip to the server before visual feedback) — no new backend performance target beyond
`patient-service`'s existing, unremarkable per-request norms (small per-patient row counts even at
the full 52-position/multi-finding scale, no batch/bulk read endpoint).

**Constraints**: RODO Art. 9 special-category data (spec.md, constitution Principle II) —
encrypted at rest/in transit via existing RDS/TLS infrastructure; append-only correction model is a
hard constraint from FR-030 (no `UPDATE`/`DELETE` may ever land on `tooth_finding`); every read/write
reuses the single existing hash-chained `audit_log_entry` table (Principle III) — no second,
parallel audit mechanism (research.md D9); ASSISTANT write parity with DOCTOR (FR-057/FR-058) is a
deliberate, spec-mandated divergence from 004's ASSISTANT-read-only scope and MUST be called out in
the PR's required security/compliance review, not treated as an inconsistency to fix.

**Scale/Scope**: Same patient population `patient-service` already serves. Each chart now holds 52
`tooth_position` rows (vs. 32 today) plus an unbounded-in-principle, bounded-in-practice
(tens-per-tooth, not thousands) set of `tooth_finding` rows including full correction history — no
pagination requirement identified in spec.md; revisit if `quickstart.md` load testing later shows
otherwise (same posture 004 took).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-checked after Phase 1 design — no changes required;
the design that emerged from Phase 0/1 satisfies every gate below exactly as anticipated.*

| Principle / Gate | Status | How this plan satisfies it |
|---|---|---|
| I. Test-First Development | PASS | `/speckit-tasks` must sequence a failing JUnit test (entity/service/controller/RBAC-denial/409-conflict) and a failing Vitest test (geometry util/component/service) before each corresponding implementation task — same Red-Green-Refactor discipline as 001/002/003/004. |
| II. Patient Data Protection & RODO (NON-NEGOTIABLE) | PASS | Special-category data (Art. 9) inherits existing at-rest/in-transit encryption (RDS/TLS, no new mechanism). RBAC scoped by job function, same `@PreAuthorize` shape as today (research.md D8). `PatientExportService` extended with full finding/canal history (data-model.md, "PatientExport"); erasure's existing deferral (`TODO(T060)`) covers the new tables by construction, not reopened here. |
| III. Full Auditability | PASS | Every read and write goes through the existing, single hash-chained `audit_log_entry` table via `PatientAuditWriter` — no new audit table, no editable/deletable path (data-model.md, research.md D9). |
| IV. Mobile-First Design | PASS | FR-048/FR-049 are explicit 320px/44×44px/WCAG-2.5.8 requirements; the arch/surface-map/detail-panel component split (research.md D11) is designed mobile-first with progressive zoom/enrichment for tablet/desktop, mirroring the mockup. |
| V. Risk-Tiered High Availability | PASS | Stays inside `patient-service`'s existing high-risk-tier failure domain (no new deployable, no new Helm release). Module boundary unchanged from 002's own plan.md. FR-073 restates this explicitly. |
| VI. Infrastructure & Delivery as Code (NON-NEGOTIABLE) | PASS | No new Terraform/Helm/Argo CD resource — Flyway migrations ship inside the existing `patient-service`/`backend` deployables through the existing GitHub Actions pipeline. The diagnosis catalog itself ships as a migration specifically so it goes through this same pipeline rather than a runtime admin screen (research.md D5). |
| Environments & Release Process | PASS | Rides `patient-service`'s existing canary progressive-delivery configuration — no new rollout object needed. |
| Development Workflow & Quality Gates | ACTION REQUIRED AT PR TIME | This change touches patient data and audit logging → the PR MUST carry a documented security/compliance self-review before merge, and auto-merge MUST NOT be enabled for it. That review MUST explicitly address FR-058's ASSISTANT/DOCTOR write-parity divergence from 004 (research.md D8) as a deliberate, spec-driven decision — not silently approve it as an oversight. |

No entries required in Complexity Tracking — no principle is being deviated from or traded off.

## Project Structure

### Documentation (this feature)

```text
specs/005-tooth-chart-diagnoses/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/
│   └── README.md         # Phase 1 output — pointer file; the actual contract changes are amended
│                          # in place in specs/001-staff-auth-rbac/ and specs/002-patient-records/
│                          # (research.md D13, same convention 004 used)
├── mockup/
│   └── odontogram-mockup.html   # pre-existing, agreed-before-planning UI reference (not a contract)
├── checklists/
│   └── requirements.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Existing web-application layout (established by 001/002, unchanged shape) — this feature replaces
the `toothchart` package inside `patient-service/` and the `tooth-chart` component tree inside
`frontend/`, plus one migration inside `backend/`:

```text
backend/                                                     # auth-service — unchanged except:
└── src/main/resources/db/migration/
    └── V14__audit_event_type_tooth_chart_diagnoses.sql      # NEW — 6 audit_event_type enum values

patient-service/
├── src/main/java/com/dentalclinic/patient/
│   ├── toothchart/                                          # REPLACED package (was: ToothState,
│   │   │                                                     # ToothStatus, ToothStateRepository,
│   │   │                                                     # ToothChartInitializer, ToothChartService)
│   │   ├── ToothChart.java                                  # NEW
│   │   ├── ToothChartRepository.java                        # NEW
│   │   ├── DentitionMode.java                                # NEW enum
│   │   ├── ToothPosition.java                                # NEW (replaces ToothState)
│   │   ├── ToothPositionRepository.java                      # NEW
│   │   ├── DentitionType.java / ToothType.java                # NEW enums (data-model.md)
│   │   ├── ToothPresence.java                                 # NEW enum (replaces ToothStatus)
│   │   ├── RootCanal.java                                     # NEW
│   │   ├── RootCanalRepository.java                           # NEW
│   │   ├── RootCanalState.java                                 # NEW enum
│   │   ├── DiagnosisCatalogEntry.java                          # NEW
│   │   ├── DiagnosisCatalogEntryRepository.java                # NEW
│   │   ├── ToothFinding.java                                   # NEW (replaces the binary status concept)
│   │   ├── ToothFindingRepository.java                         # NEW
│   │   ├── ToothChartInitializer.java                          # REWRITTEN — 52 positions, not 32
│   │   ├── ToothChartService.java                              # REWRITTEN — read/presence/dentition-mode
│   │   ├── RootCanalService.java                                # NEW
│   │   └── ToothFindingService.java                             # NEW — add/close/correct/bulk (mirrors
│   │                                                             # MedicalHistoryService's shape, research.md D3)
│   ├── api/
│   │   ├── ToothChartController.java                           # REWRITTEN — chart/presence/dentition-mode endpoints
│   │   ├── RootCanalController.java                             # NEW
│   │   ├── ToothFindingController.java                          # NEW — single + bulk create, close, correct
│   │   ├── DiagnosisCatalogController.java                      # NEW — read-only catalog search
│   │   ├── ToothChartResponse.java (+ ToothPositionResponse, RootCanalResponse,
│   │   │   ToothFindingResponse, DiagnosisCatalogEntryResponse, request records) # NEW, replaces ToothStateResponse
│   │   └── PatientDetailResponse.java                            # unchanged by this feature
│   ├── audit/PatientAuditEventType.java                          # MODIFIED — + 6 new values
│   └── rodo/PatientExportService.java                            # MODIFIED — toothChart field reshaped (data-model.md)
├── src/main/resources/db/migration/
│   └── V4__tooth_chart_diagnoses.sql                             # NEW — drops tooth_state/tooth_status,
│                                                                   # creates 5 new tables + enums + catalog seed
└── src/test/java/com/dentalclinic/patient/
    ├── toothchart/ (unit tests, replaces toothchart/ test package)
    └── api/ (ToothChartControllerTest, RootCanalControllerTest, ToothFindingControllerTest,
             DiagnosisCatalogControllerTest — RBAC + audit + 409-conflict assertions)

frontend/src/app/
├── features/patients/
│   ├── tooth-chart/                                              # REPLACED (was: single component)
│   │   ├── tooth-chart.component.ts                              # REWRITTEN — container: fetch chart,
│   │   │                                                          # own selection-state signal, wire children
│   │   ├── tooth-chart.component.spec.ts
│   │   ├── tooth-arch.component.ts                                # NEW — one arch, procedural per-tooth SVG
│   │   ├── surface-map.component.ts                                # NEW — shared strip/panel surface schema
│   │   ├── tooth-detail-panel.component.ts                         # NEW
│   │   ├── tooth-context-menu.component.ts                         # NEW — right-click/long-press quick-add
│   │   ├── tooth-geometry.ts (+ .spec.ts)                           # NEW — pure functions ported from mockup
│   │   │                                                            # (crownPath/rootGeometry/canalNodes/zoneDefs)
│   │   ├── tooth-chart.service.ts                                   # REWRITTEN — new endpoint surface
│   │   └── diagnosis-catalog.service.ts                             # NEW — search + client-side recent-codes
│   │                                                                 # cache (localStorage, research.md D12)
│   ├── patient-detail/patient-detail.component.ts                   # unchanged wiring (same tab, same guard shape)
│   └── patients.models.ts                                           # MODIFIED — replace ToothStatus/
│                                                                     # ToothStateEntry with new response types
```

**Structure Decision**: Web application, Option 2 shape (already established by 001/002) —
`backend/` (auth-service) + `patient-service/` + `frontend/`. This feature adds one migration to
`backend/`, replaces one package + adds one migration + several new/modified files in
`patient-service/`, and replaces one component with a small component tree + several modified
files in `frontend/`. No new deployable, no new top-level directory, no change to the existing
three-service topology.

## Complexity Tracking

Not applicable — the Constitution Check above has no violations to justify.

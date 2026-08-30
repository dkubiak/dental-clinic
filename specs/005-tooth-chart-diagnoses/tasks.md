---

description: "Task list template for feature implementation"
---

# Tasks: Interaktywny odontogram z rozpoznaniami i powierzchniami zębów

**Input**: Design documents from `/specs/005-tooth-chart-diagnoses/`

**Prerequisites**: plan.md, spec.md, research.md (D1-D13), data-model.md, contracts/README.md
(pointer — actual contracts amended in `specs/001-staff-auth-rbac/contracts/rbac-policy.md` and
`specs/002-patient-records/contracts/patient-api.yaml`), quickstart.md,
`mockup/odontogram-mockup.html` (visual reference, not a contract)

**Revision note**: This revision incorporates `/speckit-analyze` findings and the
session 2026-08-30 (piąta tura) clarifications: (D1) the diagnosis catalog seeds four "inne
rozpoznanie" rows, one per `AnatomicalScope`, instead of one; (G1) the single-tooth quick
context-menu (FR-020a) moves to User Story 1 — User Story 6 now only adds the multi-selection
extension (FR-020b) on top of it; (F1) SC-001/SC-012/SC-013 join plan.md's accepted manual/
structural coverage-gap list; and the main-diagram middle-strip surface map (FR-029/FR-029a/
FR-029b), frontend optimistic-lock conflict handling (FR-070/SC-010), the discard-confirmation/
save-feedback edge cases (FR-055/FR-056/FR-071), FR-002's quadrant labels, FR-025's surface-naming
rule, FR-006's responsive panel layout, and a cross-cutting FR-050/052/053 audit — all previously
uncovered — now have explicit tasks.

**Tests**: Included — constitution Principle I (Test-First Development) is NON-NEGOTIABLE; every
entity/service/controller/component task below is preceded by a failing test task, mirroring the
existing `ToothChartApiTest`/`ToothStateAutoCreationTest`/`tooth-chart.component.spec.ts` style
this feature replaces.

**Organization**: Tasks are grouped by user story (P1 rozpoznanie powierzchniowe, P2 odczyt +
korekta/zamknięcie, P3 braki zębowe/kanały, uzębienie mleczne, zaznaczenie wielokrotne) per
spec.md, after a Setup phase (migrations) and a Foundational phase (schema-backed read path +
diagram rendering skeleton + diagnosis catalog — the minimum every story needs to exist on top of).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1 (rozpoznanie), US2 (odczyt/legenda), US3 (korekta/zamknięcie), US4 (braki
  zębowe/kanały), US5 (uzębienie mleczne/mieszane), US6 (zaznaczenie wielokrotne)
- File paths are exact and repo-relative

## Path Conventions

Existing web-application layout (established by 001/002, unchanged):

- `backend/src/main/resources/db/migration/` — auth-service's Flyway history (shared
  `audit_event_type` enum)
- `patient-service/src/main/java/com/dentalclinic/patient/toothchart/` — entities, enums,
  repositories, services for the odontogram domain
- `patient-service/src/main/java/com/dentalclinic/patient/api/` — controllers and DTOs
- `patient-service/src/test/java/com/dentalclinic/patient/` — patient-service JUnit tests
- `frontend/src/app/features/patients/tooth-chart/` — Angular sources for the odontogram tab
- `design/brand/_pu-tokens.scss`, `frontend/src/styles/{brand-tokens.ts,_pu-theme.scss,contrast-pairs.ts}`
  — brand token system (003), extended with 7 new roles (research.md D10)
- `specs/001-staff-auth-rbac/contracts/rbac-policy.md`,
  `specs/002-patient-records/contracts/patient-api.yaml` — already amended by `/speckit-plan`
  (research.md D13); no further edits needed unless implementation reveals a contract gap

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Migrations and cleanup of the binary model this feature replaces outright (research.md
D1 — no production data exists, so this is deletion, not migration).

- [X] T001 Add six values (`TOOTH_POSITION_PRESENCE_CHANGED`, `DENTITION_MODE_CHANGED`,
  `ROOT_CANAL_ADDED`, `ROOT_CANAL_CHANGED`, `ROOT_CANAL_REMOVED`, `TOOTH_FINDING_ADDED`) to the
  shared `audit_event_type` Postgres enum in
  `backend/src/main/resources/db/migration/V14__audit_event_type_tooth_chart_diagnoses.sql`
  (research.md D9 — `TOOTH_CHART_VIEWED` is reused as-is; `TOOTH_STATE_CHANGED` is left inert, never
  removed from the enum)
- [X] T002 Add the six new values to `patient-service`'s own enum mirror in
  `patient-service/src/main/java/com/dentalclinic/patient/audit/PatientAuditEventType.java`
  (depends on T001)
- [X] T003 Create `patient-service/src/main/resources/db/migration/V4__tooth_chart_diagnoses.sql`:
  drop `tooth_state` table and `tooth_status` enum (research.md D1); create new enums
  (`dentition_mode`, `dentition_type`, `tooth_type`, `tooth_presence`, `root_canal_state`,
  `diagnosis_category`, `anatomical_scope`, `finding_layer`, `tooth_surface`,
  `finding_clinical_status`, `finding_record_status`, `finding_author_role`); create tables
  `tooth_chart`, `tooth_position` (with `version` for optimistic locking), `root_canal` (with
  `version`), `diagnosis_catalog_entry`, `tooth_finding` (with the partial unique index
  `UNIQUE (supersedes_finding_id) WHERE supersedes_finding_id IS NOT NULL`, research.md D7) exactly
  per data-model.md's column lists and FKs; seed `diagnosis_catalog_entry` with the full FR-015
  catalog (≥40 rows across the seven FR-014 categories), setting `quick_access` per research.md
  D12, `allowed_for_missing_tooth` per FR-041 examples (implant, przęsło mostu, stan po ekstrakcji),
  `deciduous_allowed` where an entry is adult-only, and **exactly four `requires_free_text = true`
  rows — one "inne rozpoznanie" row per `anatomical_scope` value** (FR-011a, session 2026-08-30
  piąta tura, data-model.md)
- [X] T004 [P] Delete the obsolete binary-model Java files this feature replaces outright:
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothState.java`,
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothStateRepository.java`,
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothStatus.java`,
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothStateResponse.java`
- [X] T005 [P] Delete the obsolete tests these files leave orphaned:
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothStateAutoCreationTest.java`,
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartApiTest.java` (superseded by
  `ToothChartControllerTest.java`/`DiagnosisCatalogControllerTest.java` in Phase 2)

**Checkpoint**: Schema replaced, obsolete code removed; no application code depends on the new
schema yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: The minimum every user story needs to exist on top of: all new enums/entities, a
read-only chart endpoint, the diagnosis catalog, and the procedural tooth-diagram rendering
skeleton (research.md D11) — none of this is specific to one user story, and US1's own acceptance
scenario 1 (empty odontogram renders) already exercises most of it.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### New enums (`patient-service`)

- [X] T006 [P] Create `DentitionMode.java` (`PERMANENT`, `DECIDUOUS`, `MIXED`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/DentitionMode.java`
- [X] T007 [P] Create `DentitionType.java` (`PERMANENT`, `DECIDUOUS`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/DentitionType.java`
- [X] T008 [P] Create `ToothType.java` (`INCISOR`, `CANINE`, `PREMOLAR`, `MOLAR`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothType.java`
- [X] T009 [P] Create `ToothPresence.java` (`PRESENT`, `EXTRACTED`, `CONGENITALLY_MISSING`,
  `UNERUPTED`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothPresence.java`
- [X] T010 [P] Create `RootCanalState.java` (`NEEDS_TREATMENT`, `TREATED`, `UNDERTREATED`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/RootCanalState.java`
- [X] T011 [P] Create `DiagnosisCategory.java` (the seven FR-014 groups) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/DiagnosisCategory.java`
- [X] T012 [P] Create `AnatomicalScope.java` (`SURFACE`, `WHOLE_TOOTH`, `ROOT_PERIAPICAL`,
  `PERIODONTIUM`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/AnatomicalScope.java`
- [X] T013 [P] Create `FindingLayer.java` (`DIAGNOSIS`, `EXISTING_STATE`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/FindingLayer.java`
- [X] T014 [P] Create `ToothSurface.java` (`MESIAL`, `DISTAL`, `VESTIBULAR`, `LINGUAL_PALATAL`,
  `OCCLUSAL_INCISAL`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothSurface.java`
- [X] T015 [P] Create `FindingClinicalStatus.java` (`ACTIVE`, `RESOLVED`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/FindingClinicalStatus.java`
- [X] T016 [P] Create `FindingRecordStatus.java` (`CURRENT`, `SUPERSEDED`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/FindingRecordStatus.java`
- [X] T017 [P] Create `FindingAuthorRole.java` (`DOCTOR`, `ASSISTANT`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/FindingAuthorRole.java`

### New entities/repositories (`patient-service`)

- [X] T018 Create `ToothChart` JPA entity (`patientRecordId` FK unique, `dentitionMode`,
  `dentitionModeSetBy`, `dentitionModeSetAt` per data-model.md) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothChart.java` (depends on
  T006)
- [X] T019 Create `ToothChartRepository.java` (`findByPatientRecordId`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothChartRepository.java`
  (depends on T018)
- [X] T020 Create `ToothPosition` JPA entity (`toothChartId` FK, `fdiNumber` unique-per-chart,
  `dentitionType`, `toothType`, `presence`, `presenceDate`, `@Version version`, `updatedAt`,
  `updatedBy` per data-model.md) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothPosition.java` (depends
  on T007, T008, T009)
- [X] T021 Create `ToothPositionRepository.java` (`findByToothChartId`,
  `findByToothChartIdAndFdiNumber`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothPositionRepository.java`
  (depends on T020)
- [X] T022 Create `RootCanal` JPA entity (`toothPositionId` FK, `name`, `state`, `removed`,
  `@Version version`, `createdAt`/`createdBy`, `updatedAt`/`updatedBy` per data-model.md) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/RootCanal.java` (depends on
  T010)
- [X] T023 Create `RootCanalRepository.java` (`findByToothPositionIdAndRemovedFalse`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/RootCanalRepository.java`
  (depends on T022)
- [X] T024 Create `DiagnosisCatalogEntry` JPA entity (`code` unique, `namePl`, `category`,
  `anatomicalScope`, `layer`, `icd10Code`, `severityOptions`, `allowedForMissingTooth`,
  `deciduousAllowed`, `quickAccess`, `requiresFreeText`, `catalogVersion` per data-model.md) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/DiagnosisCatalogEntry.java`
  (depends on T011, T012, T013)
- [X] T025 Create `DiagnosisCatalogEntryRepository.java` (`findByNamePlContainingIgnoreCaseOrCodeContainingIgnoreCase`,
  `findByQuickAccessTrue`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/DiagnosisCatalogEntryRepository.java`
  (depends on T024)
- [X] T026 Create `ToothFinding` JPA entity (`toothPositionId` FK, `diagnosisCatalogEntryId` FK,
  `surfaces` array, `rootCanalId` nullable FK, `severity`, `freeTextDescription`, `note`,
  `diagnosisDate`, `resolvedDate`, `clinicalStatus`, `recordStatus`, `supersedesFindingId`
  self-FK, `authorAccountId`, `authorRole`, `createdAt` per data-model.md) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothFinding.java` (depends on
  T014, T015, T016, T017)
- [X] T027 Create `ToothFindingRepository.java`
  (`findByToothPositionIdAndRecordStatus`, `findByToothPositionId` for full history) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothFindingRepository.java`
  (depends on T026)

### Tests for Foundational (write FIRST, ensure they FAIL)

- [X] T028 [P] Failing JUnit test: `ToothChartInitializer` creates one `ToothChart` row and all 52
  `ToothPosition` rows (32 permanent FDI 11-48 + 20 deciduous FDI 51-85, correct
  `dentitionType`/`toothType` per position) at patient-record creation time, regardless of
  `dentitionMode` (research.md D2) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothChartInitializerTest.java`
- [X] T029 [P] Failing JUnit test: the seeded `diagnosis_catalog_entry` table contains every FR-015
  code with a unique `code`; **exactly four `requiresFreeText = true` rows, one per
  `AnatomicalScope` value** (`SURFACE`, `WHOLE_TOOTH`, `ROOT_PERIAPICAL`, `PERIODONTIUM` — FR-011a,
  data-model.md); and every row's `anatomicalScope`/`layer` matches its FR-014/FR-016
  classification in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/DiagnosisCatalogSeedTest.java`
- [X] T030 [P] Failing JUnit test: `ToothChartService.getChart` returns 32 `PERMANENT` positions, all
  `presence: PRESENT`, zero findings, for a fresh adult patient (US1 Acceptance Scenario 1) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothChartServiceTest.java`
- [X] T031 [P] Failing JUnit RBAC/audit test: `GET /patients/{id}/tooth-chart` — DOCTOR/ASSISTANT
  200, RECEPTION 404, ADMINISTRATOR 404, nonexistent patient 404 (indistinguishable from a denied
  role, FR-059), one `TOOTH_CHART_VIEWED` audit row per successful read, in
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartControllerTest.java`
- [X] T032 [P] Failing JUnit RBAC test: `GET /diagnosis-catalog` — DOCTOR/ASSISTANT 200 with `q` and
  `quickAccessOnly` filtering, RECEPTION 404, no POST/PATCH/DELETE mapping exists (FR-011) in
  `patient-service/src/test/java/com/dentalclinic/patient/api/DiagnosisCatalogControllerTest.java`
- [X] T033 [P] Failing Vitest test: `tooth-geometry.ts`'s pure functions (`crownPath`,
  `rootGeometry`, `canalNodes`, `zoneDefs`, ported from the mockup) produce the correct cusp/root
  count and surface-zone set for each `ToothType` (incisor/canine/premolar/molar), offer
  incisal-not-occlusal for incisors/canines vs. occlusal-not-incisal for premolars/molars (FR-024),
  and apply FR-025's naming rule (podniebienna for upper vs. językowa for lower on the same zone;
  wargowa for anterior vs. policzkowa for posterior teeth) in
  `frontend/src/app/features/patients/tooth-chart/tooth-geometry.spec.ts`
- [X] T034 [P] Failing Vitest test: `tooth-chart.service.ts`'s `getChart` and
  `searchDiagnosisCatalog` methods in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.service.spec.ts`
- [X] T035 [P] Failing Vitest test: `tooth-chart.component.ts` renders both arches simultaneously
  (FR-001) with readable quadrant labels (1-4 permanent / 5-8 deciduous, FR-002), and shows the
  FR-054 loading/empty/error states (empty: "brak odnotowanych zmian" message, US1 Scenario 1), in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts`

### Implementation for Foundational

- [X] T036 Rewrite `ToothChartInitializer` — create the `ToothChart` row plus all 52
  `ToothPosition` rows, `dentitionMode` defaulted from age at creation time (FR-044: `DECIDUOUS`
  <6y, `MIXED` 6-13y, `PERMANENT` else) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothChartInitializer.java`
  (depends on T018-T021, T028)
- [X] T037 Rewrite `ToothChartService` with `getChart(patientId)` (read positions + canals +
  current findings, audit `TOOTH_CHART_VIEWED`) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothChartService.java`
  (depends on T036, T030)
- [X] T038 [P] Create `ToothChartResponse.java`, `ToothPositionResponse.java`,
  `RootCanalResponse.java` DTOs per contracts/patient-api.yaml schemas in
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothChartResponse.java`,
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothPositionResponse.java`,
  `patient-service/src/main/java/com/dentalclinic/patient/api/RootCanalResponse.java` (depends on
  T020, T022)
- [X] T039 [P] Create `DiagnosisCatalogEntryResponse.java` DTO in
  `patient-service/src/main/java/com/dentalclinic/patient/api/DiagnosisCatalogEntryResponse.java`
  (depends on T024)
- [X] T040 Create `DiagnosisCatalogService.java` — search by name/code fragment (FR-013) and
  `quickAccessOnly` filter (FR-020) in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/DiagnosisCatalogService.java`
  (depends on T025, T029)
- [X] T041 Create `DiagnosisCatalogController.java` — `GET /diagnosis-catalog`,
  `@PreAuthorize("hasAnyRole('DOCTOR','ASSISTANT')")`, no write mapping (FR-011) in
  `patient-service/src/main/java/com/dentalclinic/patient/api/DiagnosisCatalogController.java`
  (depends on T039, T040, T032)
- [X] T042 Rewrite `ToothChartController` with `GET /patients/{patientId}/tooth-chart`,
  `@PreAuthorize("hasAnyRole('DOCTOR','ASSISTANT')")`, deny→404 (rbac-policy.md rule 2/8) in
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothChartController.java` (depends
  on T037, T038, T031)
- [X] T043 [P] Replace `ToothStatus`/`ToothStateEntry` with `ToothChart`, `ToothPosition`,
  `RootCanal`, `DiagnosisCatalogEntry`, `ToothFinding` TypeScript interfaces (per
  contracts/patient-api.yaml schemas) in
  `frontend/src/app/features/patients/patients.models.ts`
- [X] T044 Rewrite `tooth-chart.service.ts` with `getChart` and `searchDiagnosisCatalog` HTTP
  methods (thin-relay pattern, mirrors `patients.service.ts`) in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.service.ts` (depends on T043, T034)
- [X] T045 [P] Create `tooth-geometry.ts` — `crownPath`/`rootGeometry`/`canalNodes`/`zoneDefs` pure
  functions parameterized by cusp/root count per tooth type (ported from the mockup, research.md
  D11); a Polish anatomical-name lookup per FDI number (ported from the mockup's `meta(fdi)`,
  FR-005/FR-053); and FR-025's surface-naming rule (podniebienna vs. językowa by jaw,
  wargowa vs. policzkowa by anterior/posterior position) in
  `frontend/src/app/features/patients/tooth-chart/tooth-geometry.ts` (depends on T033)
- [X] T046 Create `diagnosis-catalog.service.ts` — wraps `searchDiagnosisCatalog` and maintains the
  client-side "ostatnio używane" cache in `localStorage`, keyed by clinician account id
  (research.md D12) in
  `frontend/src/app/features/patients/tooth-chart/diagnosis-catalog.service.ts` (depends on T044)
- [X] T047 Create `tooth-arch.component.ts` — renders one arch procedurally from
  `tooth-geometry.ts`, teeth oriented crowns-together/roots-outward, with the arch's quadrant
  labels rendered per FR-002, in
  `frontend/src/app/features/patients/tooth-chart/tooth-arch.component.ts` (depends on T045)
- [X] T048 Rewrite `tooth-chart.component.ts` as a container: fetch the chart, render both arches
  via `tooth-arch.component.ts`, own the selection-state signal (empty for now), FR-054
  loading/empty/error states in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts` (depends on T047, T044,
  T035)
- [X] T049 [P] Add seven new token roles (`tooth-root-fill`, `tooth-restored-fill`,
  `tooth-restored-stroke`, `tooth-closed-stroke`, `tooth-absent`, `canal-treat`, `canal-done`) to
  `design/brand/_pu-tokens.scss`, `frontend/src/styles/brand-tokens.ts`,
  `frontend/src/styles/_pu-theme.scss` (as `light-dark(#light, #dark)`), and
  `frontend/src/styles/contrast-pairs.ts` (research.md D10) — covered automatically by the existing
  `token-parity.spec.ts`/`contrast-audit.spec.ts` once added

**Checkpoint**: Odontogram renders read-only with correct anatomy, quadrant labels, and empty
state; diagnosis catalog is searchable. Nothing is saveable yet — user stories add that.

---

## Phase 3: User Story 1 - Lekarz odnotowuje rozpoznanie na konkretnej powierzchni zęba (Priority: P1) 🎯 MVP

**Goal**: DOCTOR/ASSISTANT select a tooth, pick a surface-scoped diagnosis from the catalog either
through the full form or the quick context-menu, indicate the affected surface(s), and save — the
diagram and detail panel immediately reflect the new finding, persisted and readable by any
authorized user (spec.md US1, including its Acceptance Scenario 8 quick-menu path).

**Independent Test**: Log in as DOCTOR, open a patient with a default-healthy odontogram, add one
surface-scoped finding on one tooth via the form, add a second via the quick context-menu, reload
the page, and verify the diagram and detail panel show both — without corrections, special states,
deciduous dentition, or multi-tooth selection.

### Tests for User Story 1 ⚠️

- [X] T050 [P] [US1] Failing JUnit test: `ToothFindingService.addFinding` requires ≥1 surface for
  `SURFACE`-scope catalog entries and forbids surfaces for other scopes (FR-022/FR-023), requires
  `freeTextDescription` iff `requiresFreeText` (FR-011a), rejects a `diagnosisDate` in the future
  or before the patient's date of birth (FR-036), and correctly resolves each of the four "inne
  rozpoznanie" rows to its own `AnatomicalScope` (D1) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothFindingServiceTest.java`
- [X] T051 [US1] Failing JUnit API/RBAC test: `POST /patients/{id}/tooth-chart/findings` — 201 for
  DOCTOR/ASSISTANT with `recordStatus: CURRENT`/`clinicalStatus: ACTIVE`/`authorRole` set correctly,
  404 for RECEPTION, 400 for a missing required surface, one `TOOTH_FINDING_ADDED` audit row with
  `before_state: null` in
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothFindingControllerTest.java`
- [X] T052 [P] [US1] Failing Vitest test: `tooth-detail-panel.component.ts` shows FDI number, Polish
  anatomical name, the surface map, an empty finding list for a fresh tooth (US1 Scenario 2); blocks
  save without a surface for a `SURFACE`-scope entry (US1 Scenario 3); hides the surface picker for
  a `WHOLE_TOOTH`-scope entry (US1 Scenario 7); renders side-by-side with the diagram on a wide
  viewport and as a slide-over drawer on a narrow one, with the selected tooth never fully hidden by
  either layout (FR-006); asks for confirmation before discarding an unsaved form (FR-055); keeps
  entered field values after a failed save so the user never has to re-enter them (FR-071); and
  shows a visible, no-scroll-required confirmation on a successful save (FR-056) in
  `frontend/src/app/features/patients/tooth-chart/tooth-detail-panel.component.spec.ts`
- [X] T053 [P] [US1] Failing Vitest test: `surface-map.component.ts` offers an incisal surface for
  incisors/canines and an occlusal surface for premolars/molars, never both (FR-024, US1 Scenario
  6); clicking a zone toggles its selection in
  `frontend/src/app/features/patients/tooth-chart/surface-map.component.spec.ts`
- [X] T054 [P] [US1] Failing Vitest test: `tooth-context-menu.component.ts` opens on right-click and
  on long-press for a **single** tooth or surface zone (no multi-selection required), lists
  recent-used/quick-access/missing-tooth/restoration sections, saves the chosen entry immediately
  via the same `addFinding` path as the full form, and offers an instant undo implemented as a
  correct-supersede call (FR-020a, US1 Acceptance Scenario 8; session 2026-08-30 piąta tura) in
  `frontend/src/app/features/patients/tooth-chart/tooth-context-menu.component.spec.ts`

### Implementation for User Story 1

- [X] T055 [US1] Implement `ToothFindingService.addFinding(...)` — validates scope/surfaces/date,
  persists a `CURRENT`/`ACTIVE` `ToothFinding`, writes `TOOTH_FINDING_ADDED` with `before_state:
  null` in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothFindingService.java`
  (depends on T026, T027, T050)
- [X] T056 [P] [US1] Create `ToothFindingResponse.java` and `ToothFindingCreateRequest.java` per
  contracts/patient-api.yaml schemas in
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothFindingResponse.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothFindingCreateRequest.java`
  (depends on T026)
- [X] T057 [US1] Create `ToothFindingController.java` — `POST
  /patients/{patientId}/tooth-chart/findings`, `@PreAuthorize("hasAnyRole('DOCTOR','ASSISTANT')")`
  in `patient-service/src/main/java/com/dentalclinic/patient/api/ToothFindingController.java`
  (depends on T055, T056, T051)
- [X] T058 [US1] Populate `currentFindings` (only `recordStatus: CURRENT`) on
  `ToothPositionResponse`/`ToothChartResponse` in
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothChartResponse.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothPositionResponse.java` (depends
  on T038, T055)
- [X] T059 [P] [US1] Add `ToothFinding` and `ToothFindingCreateRequest` TypeScript types to
  `frontend/src/app/features/patients/patients.models.ts` (depends on T043)
- [X] T060 [US1] Add `addFinding` method to `tooth-chart.service.ts` (depends on T059, T044)
- [X] T061 [US1] Implement `surface-map.component.ts` — clickable zones per tooth type, a tooltip
  with the surface name before click, and a visibly distinct selected/empty/has-entry state without
  letter labels on the main diagram (FR-024, FR-029a) in
  `frontend/src/app/features/patients/tooth-chart/surface-map.component.ts` (depends on T053, T045)
- [X] T062 [US1] Implement `tooth-detail-panel.component.ts` — catalog search (via
  `diagnosis-catalog.service.ts`), severity picker (FR-018), optional note (FR-017), surface
  requirement enforcement, save wiring, the FR-006 responsive side-by-side/drawer layout, the
  FR-055 discard-confirmation guard, FR-071 form-state retention on a failed save, and the FR-056
  save-confirmation feedback in
  `frontend/src/app/features/patients/tooth-chart/tooth-detail-panel.component.ts` (depends on
  T052, T046, T061, T060)
- [X] T063 [US1] Wire tooth selection on `tooth-arch.component.ts` to open the detail panel, and
  re-render the diagram to show "z aktywnym rozpoznaniem" after a successful save, in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts` (depends on T048, T062)
- [X] T064 [US1] Add keyboard navigation (arrow keys between teeth, FR-052) and screen-reader text
  per position (FDI + anatomical name + status summary, FR-053) to
  `frontend/src/app/features/patients/tooth-chart/tooth-arch.component.ts` and
  `tooth-detail-panel.component.ts` (depends on T063)
- [X] T065 [US1] Create `tooth-context-menu.component.ts` — the single-tooth quick-add path:
  recent-used via `diagnosis-catalog.service.ts`, quick-access sections, immediate save via
  `addFinding` (T060), instant undo via a correct-supersede call (FR-020a) in
  `frontend/src/app/features/patients/tooth-chart/tooth-context-menu.component.ts` (depends on
  T054, T060, T046)
- [X] T066 [US1] Wire right-click and long-press handlers on `tooth-arch.component.ts` and
  `surface-map.component.ts` to open `tooth-context-menu.component.ts` for a single tooth — this
  path MUST NOT require any multi-selection to be active (FR-020a) (depends on T065, T063)

**Checkpoint**: User Story 1 is fully functional and independently testable — MVP scope, including
the quick-menu chairside path.

---

## Phase 4: User Story 2 - Lekarz odczytuje pełny obraz uzębienia z jednego widoku (Priority: P2)

**Goal**: A legend explains every visual marking, a layer filter toggles rozpoznanie/stan
istniejący/wszystkie, a per-tooth "wiele wpisów" indicator covers overflow, a "historia zęba"
disclosure exposes resolved/superseded findings, and — the core of "reading the whole picture from
one view" — a middle-strip surface-map row between the two arches lets every tooth's surfaces be
read and clicked directly from the main diagram, with 1×/2×/3× zoom (spec.md US2; FR-029/FR-029a/
FR-029b, FR-049).

**Independent Test**: On a patient with several pre-existing findings, verify the legend, layer
filter, tooth history disclosure, and middle-strip surface map present and accept input correctly —
without using the detail-panel form.

### Tests for User Story 2 ⚠️

- [ ] T067 [P] [US2] Failing JUnit test: `GET
  /patients/{id}/tooth-chart/positions/{fdiNumber}/history` returns current, resolved, and
  superseded findings in chronological order, audited as `TOOTH_CHART_VIEWED`
  (`metadata.detail = "position-history"`) in
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartControllerTest.java`
  (extends T031)
- [ ] T068 [P] [US2] Failing Vitest test: the legend lists every visual state/layer/surface symbol
  in Polish (FR-008), and the layer filter dims/hides `EXISTING_STATE` markers while keeping
  `DIAGNOSIS` markers visible, purely client-side (FR-009) in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts` (extends T035)
- [ ] T069 [P] [US2] Failing Vitest test: a tooth whose findings can't all render shows the "wiele
  wpisów" indicator (FR-010); the "historia zęba" disclosure is collapsed by default and reveals
  resolved/superseded entries only on expansion (FR-034) in
  `frontend/src/app/features/patients/tooth-chart/tooth-detail-panel.component.spec.ts` (extends
  T052)
- [ ] T070 [P] [US2] Failing Vitest test: `tooth-chart.component.ts` renders one `surface-map`
  instance per visible tooth column in a middle strip between the two arches (FR-029); each zone is
  directly clickable on mouse, pen, and touch and selects the matching tooth+surface without first
  opening the detail panel (FR-029a); a zoom control reaches ≥24×24px zones at its first level and
  ≥44×44px at its highest (FR-029b/FR-049); the enlarged diagram scrolls horizontally only within
  its own container, never as page scroll; and the selected tooth stays in view across zoom-level
  changes in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts` (extends T035)

### Implementation for User Story 2

- [ ] T071 [US2] Add `getPositionHistory(patientId, fdiNumber)` to `ToothChartService.java` (depends
  on T067)
- [ ] T072 [US2] Add `GET /patients/{patientId}/tooth-chart/positions/{fdiNumber}/history` to
  `ToothChartController.java` (depends on T071)
- [ ] T073 [US2] Add `getPositionHistory` method to `tooth-chart.service.ts` (depends on T072)
- [ ] T074 [US2] Add the legend UI (every state/layer/surface symbol, Polish text, FR-008) to
  `tooth-chart.component.ts` (depends on T068)
- [ ] T075 [US2] Add the layer-filter control ("rozpoznanie"/"stan istniejący"/"wszystkie") to
  `tooth-chart.component.ts`, purely client-side (FR-009), with the FR-039/FR-050 non-color cue
  preserved when dimmed (depends on T068)
- [ ] T076 [US2] Add the "wiele wpisów" indicator to `tooth-arch.component.ts` and the "historia
  zęba" disclosure (backed by `getPositionHistory`) to `tooth-detail-panel.component.ts` (depends on
  T069, T073)
- [ ] T077 [US2] Render the middle-strip surface-map row in `tooth-chart.component.ts` — one
  `surface-map.component.ts` instance per visible tooth column, positioned between the upper and
  lower arch, aligned to its corresponding tooth's column (FR-029) in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts` (depends on T070, T061)
- [ ] T078 [US2] Add the 1×/2×/3× diagram zoom control to `tooth-chart.component.ts` (FR-029b) —
  horizontal scroll confined to the diagram's own container (never page scroll, FR-049), keeps the
  currently selected tooth in view across zoom-level changes (depends on T070, T077)
- [ ] T079 [US2] Wire direct surface-zone clicks on the middle strip to select the matching
  tooth+surface and reuse the exact save flow User Story 1 already built (open/update the detail
  panel or route through the quick context-menu), without requiring the panel to be opened first
  (FR-029a) in `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts` (depends
  on T077, T063)

**Checkpoint**: User Stories 1 AND 2 both work independently; the main diagram is now a complete
read surface, not just a click-to-open-panel shell.

---

## Phase 5: User Story 3 - Lekarz koryguje wpis i zamyka rozpoznanie po leczeniu (Priority: P2)

**Goal**: Closing a finding (treatment done) and correcting a mistaken finding are both
implemented as the same supersede-then-insert primitive (research.md D3) — the original is never
edited or deleted (FR-030/FR-033) — and a stale write is never silently lost: the frontend surfaces
a conflict message with a reload option (FR-070/SC-010).

**Independent Test**: Close an existing finding and verify the tooth stops showing "aktywne
rozpoznanie" while the original remains in history as `SUPERSEDED`; separately correct a different
finding and verify both versions remain linked in history; provoke a concurrent-correction 409 and
verify the UI shows a message with a reload action rather than failing silently.

### Tests for User Story 3 ⚠️

- [X] T080 [P] [US3] Failing JUnit test: `closeFinding`/`correctFinding` insert a new `CURRENT` row
  and flip the original to `SUPERSEDED` atomically; a second concurrent correction attempt on the
  same original hits the partial unique index and fails (research.md D7) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothFindingServiceTest.java`
  (extends T050)
- [X] T081 [US3] Failing JUnit API test: `POST .../findings/{id}/close` and `.../correct` — 201 with
  `supersedesFindingId` set and `TOOTH_FINDING_ADDED` audited with `before_state` = the superseded
  snapshot; repeating `/close` on an already-superseded finding → 409; an ASSISTANT correcting a
  DOCTOR-authored finding shows both authors in `.../history` (FR-058) in
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothFindingControllerTest.java`
  (extends T051)
- [ ] T082 [P] [US3] Failing Vitest test: "Zamknij rozpoznanie" requires a `resolvedDate` and
  "Koryguj" pre-fills the current values and submits with `supersedesFindingId` set in
  `frontend/src/app/features/patients/tooth-chart/tooth-detail-panel.component.spec.ts` (extends
  T052)
- [ ] T083 [P] [US3] Failing Vitest test: a `409` response from any `tooth-chart.service.ts` write
  method surfaces a readable Polish message with a "przeładuj" (reload) action in
  `tooth-chart.component.ts`, never a silent failure or a generic unhandled-error state (FR-070/
  SC-010) in `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts`
  (extends T035)

### Implementation for User Story 3

- [X] T084 [US3] Implement `closeFinding`/`correctFinding` on `ToothFindingService.java` — single
  supersede-then-insert transaction (research.md D3) (depends on T080)
- [X] T085 [P] [US3] Create `FindingCloseRequest.java` per contracts/patient-api.yaml in
  `patient-service/src/main/java/com/dentalclinic/patient/api/FindingCloseRequest.java` (depends on
  T026)
- [X] T086 [US3] Add `POST .../findings/{findingId}/close` and `.../correct` to
  `ToothFindingController.java` (depends on T084, T085, T081)
- [X] T087 [US3] Add `closeFinding`/`correctFinding` methods to `tooth-chart.service.ts` (depends on
  T086)
- [ ] T088 [US3] Add "Zamknij rozpoznanie"/"Koryguj" actions to `tooth-detail-panel.component.ts`,
  diagram stops showing "aktywne rozpoznanie" for a closed finding (depends on T082, T087, T076)
- [ ] T089 [US3] Add a shared, typed 409-conflict handler to `tooth-chart.service.ts` (catches the
  optimistic-lock/supersede-conflict response) and a reload-prompt affordance to
  `tooth-chart.component.ts` (FR-070/SC-010) (depends on T083, T087)

**Checkpoint**: User Stories 1-3 all work independently, including conflict handling for the
finding-correction path.

---

## Phase 6: User Story 4 - Lekarz odnotowuje braki zębowe i stany nie-chorobowe (Priority: P3)

**Goal**: Positions can be marked `EXTRACTED`/`CONGENITALLY_MISSING`/`UNERUPTED` with optimistic
locking (FR-070); missing-tooth positions block surface-scoped findings but allow
`allowedForMissingTooth` ones (implant, przęsło mostu); root canals (up to 6/tooth) can be added,
renamed, state-changed, and soft-removed without breaking references (spec.md US4, section I); the
same conflict-handling UI from User Story 3 covers these endpoints too.

**Independent Test**: Mark one tooth per special state and verify the diagram distinguishes each
from healthy/diseased teeth; add a root canal to a present tooth and verify its treatment-state
color renders inside the root silhouette; provoke a stale-`expectedVersion` 409 on a presence/canal
write and verify the same reload prompt from User Story 3 appears.

### Tests for User Story 4 ⚠️

- [ ] T090 [P] [US4] Failing JUnit test: presence `PATCH` with a stale `expectedVersion` → 409
  (FR-070); setting `EXTRACTED` blocks a new `SURFACE`-scope finding (409, FR-040) but allows an
  `allowedForMissingTooth` entry (201, FR-041) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothChartServiceTest.java`
  (extends T030)
- [ ] T091 [P] [US4] Failing JUnit test: `RootCanalService` enforces max 6 non-removed canals per
  position and requires `presence = PRESENT` to add one; rename/state-change with a stale
  `expectedVersion` → 409; soft-removing a canal never deletes or hides findings referencing it
  (FR-068) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/RootCanalServiceTest.java`
- [ ] T092 [US4] Failing JUnit API test: `PATCH .../positions/{fdi}/presence` and
  `POST/PATCH/DELETE .../canals[/{id}]` — RBAC (DOCTOR/ASSISTANT only) and audit rows
  (`TOOTH_POSITION_PRESENCE_CHANGED`, `ROOT_CANAL_ADDED`/`ROOT_CANAL_CHANGED`/`ROOT_CANAL_REMOVED`)
  in `patient-service/src/test/java/com/dentalclinic/patient/api/RootCanalControllerTest.java` and
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartControllerTest.java`
  (extends T031)
- [ ] T093 [P] [US4] Failing Vitest test: presence controls in `tooth-detail-panel.component.ts`
  mark a tooth extracted/congenitally-missing/unerupted, rendered distinctly from healthy/diseased
  without relying on color alone (FR-039) in
  `frontend/src/app/features/patients/tooth-chart/tooth-detail-panel.component.spec.ts` (extends
  T052)
- [ ] T094 [P] [US4] Failing Vitest test: root-canal add/rename/state-change/remove controls in
  `tooth-detail-panel.component.ts`, and canal treatment-state rendering (red/green/green-with-red-
  apex, FR-066a) inside the root silhouette in `tooth-arch.component.ts` in
  `frontend/src/app/features/patients/tooth-chart/tooth-arch.component.spec.ts` and
  `tooth-detail-panel.component.spec.ts` (extends T052)

### Implementation for User Story 4

- [ ] T095 [US4] Implement `changePresence(...)` on `ToothChartService.java` — `@Version`-checked
  update, blocks incompatible findings, audits `TOOTH_POSITION_PRESENCE_CHANGED` (depends on T090)
- [ ] T096 [US4] Add `PATCH /patients/{patientId}/tooth-chart/positions/{fdiNumber}/presence` to
  `ToothChartController.java` (depends on T095, T092)
- [ ] T097 [US4] Create `RootCanalService.java` — `addCanal`/`updateCanal`/`removeCanal` (soft
  delete only), audits `ROOT_CANAL_ADDED`/`ROOT_CANAL_CHANGED`/`ROOT_CANAL_REMOVED` in
  `patient-service/src/main/java/com/dentalclinic/patient/toothchart/RootCanalService.java`
  (depends on T022, T023, T091)
- [ ] T098 [P] [US4] Create `RootCanalCreateRequest.java` and `RootCanalPatchRequest.java` per
  contracts/patient-api.yaml in
  `patient-service/src/main/java/com/dentalclinic/patient/api/RootCanalCreateRequest.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/RootCanalPatchRequest.java`
- [ ] T099 [US4] Create `RootCanalController.java` — `POST/PATCH/DELETE
  .../positions/{fdiNumber}/canals[/{canalId}]`, `@PreAuthorize("hasAnyRole('DOCTOR','ASSISTANT')")`
  in `patient-service/src/main/java/com/dentalclinic/patient/api/RootCanalController.java` (depends
  on T097, T098, T092)
- [ ] T100 [US4] Add `changePresence`/`addCanal`/`updateCanal`/`removeCanal` methods to
  `tooth-chart.service.ts` (depends on T096, T099)
- [ ] T101 [US4] Add presence controls to `tooth-detail-panel.component.ts` and the
  `tooth-absent`-token rendering to `tooth-arch.component.ts` (depends on T093, T100)
- [ ] T102 [US4] Add root-canal controls to `tooth-detail-panel.component.ts` and canal rendering
  inside the root silhouette to `tooth-arch.component.ts` using the `canal-treat`/`canal-done`
  tokens plus the apex-split non-color cue for `UNDERTREATED` (FR-066a) (depends on T094, T100,
  T049)
- [ ] T103 [US4] Extend the shared 409-conflict handler built in User Story 3 (T089) to cover
  presence/canal `expectedVersion` conflicts — same reload-prompt UI, no new mechanism (depends on
  T089, T100)

**Checkpoint**: User Stories 1-4 all work independently.

---

## Phase 7: User Story 5 - Lekarz pracuje na uzębieniu mlecznym i mieszanym (Priority: P3)

**Goal**: Dentition mode defaults from age and can be overridden without ever deleting or hiding
existing findings (FR-044/FR-047); mixed mode renders deciduous and permanent positions
simultaneously, visually distinguished (spec.md US5).

**Independent Test**: Open a child patient's chart, confirm deciduous mode defaults, switch to
mixed, and confirm no existing finding disappears or becomes inaccessible.

### Tests for User Story 5 ⚠️

- [ ] T104 [P] [US5] Failing JUnit test: `ToothChartInitializer`/`ToothChartService` default
  `dentitionMode` from age (`DECIDUOUS` <6y, `MIXED` 6-13y, `PERMANENT` else, FR-044); changing mode
  never deletes/modifies any `ToothPosition`/`ToothFinding` row (FR-047) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothChartServiceTest.java`
  (extends T030)
- [ ] T105 [US5] Failing JUnit API test: `PATCH /patients/{id}/tooth-chart/dentition-mode` succeeds
  for DOCTOR/ASSISTANT, persists across a subsequent `GET`, audited as `DENTITION_MODE_CHANGED` in
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartControllerTest.java`
  (extends T031)
- [ ] T106 [P] [US5] Failing Vitest test: `tooth-chart.component.ts` renders the 20 deciduous
  positions by default for a child patient, and both deciduous and permanent positions in mixed
  mode, visually distinguished by more than numbering (FR-046) in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts` (extends T035)

### Implementation for User Story 5

- [ ] T107 [US5] Implement `changeDentitionMode(...)` on `ToothChartService.java` — audits
  `DENTITION_MODE_CHANGED`, flags in the response when the new mode hides positions with existing
  findings (FR-047) (depends on T104)
- [ ] T108 [US5] Add `PATCH /patients/{patientId}/tooth-chart/dentition-mode` to
  `ToothChartController.java` (depends on T107, T105)
- [ ] T109 [P] [US5] Create `DentitionModePatchRequest.java` in
  `patient-service/src/main/java/com/dentalclinic/patient/api/DentitionModePatchRequest.java`
- [ ] T110 [US5] Add `changeDentitionMode` method to `tooth-chart.service.ts` (depends on T108,
  T109)
- [ ] T111 [US5] Add the dentition-mode switcher UI to `tooth-chart.component.ts` and
  deciduous/mixed rendering (smaller/marked deciduous silhouettes, FR-046) to
  `tooth-arch.component.ts` (depends on T106, T110)

**Checkpoint**: User Stories 1-5 all work independently.

---

## Phase 8: User Story 6 - Operator oznacza stan wielu zębów i wielu części naraz (Priority: P3)

**Goal**: Multi-select across teeth and parts, plus quadrant/arch/segment/drag shortcuts
(FR-004a-c), producing N independent `ToothFinding` rows, never a shared batch entity (research.md
D6). The single-tooth quick context-menu already built in User Story 1 gains one extension here:
applying a chosen entry to an entire active multi-selection instead of just the tooth it was
invoked on (FR-020b) — this story does not build the menu itself.

**Independent Test**: Select six anterior teeth via the segment shortcut, save one finding through
the bulk endpoint, and verify six independent findings exist, each correctable on its own; open the
existing quick context-menu on a multi-selection and confirm it applies to every selected tooth.

### Tests for User Story 6 ⚠️

- [ ] T112 [P] [US6] Failing JUnit test: `addFindingsBulk` creates one independent `ToothFinding`
  per applicable position inside a single transaction, skips inapplicable positions with a
  human-readable reason, and never fails the whole call (FR-004a, US6 scenario 3/4) in
  `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothFindingServiceTest.java`
  (extends T050)
- [ ] T113 [US6] Failing JUnit API test: `POST /patients/{id}/tooth-chart/findings/bulk` returns
  `created`/`skipped` per contracts/patient-api.yaml, one `TOOTH_FINDING_ADDED` audit row per
  created finding in
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothFindingControllerTest.java`
  (extends T051)
- [ ] T114 [P] [US6] Failing Vitest test: multi-select state in `tooth-chart.component.ts` — select
  multiple teeth/parts, quadrant/arch/anterior-segment shortcuts, drag-select across adjacent teeth,
  a visible counter, deselecting one tooth leaves the rest intact, and clearing requires an explicit
  action (FR-004a-c) in
  `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts` (extends T035)
- [ ] T115 [P] [US6] Failing Vitest test: `tooth-context-menu.component.ts` (built in US1, T065),
  when invoked while a multi-selection is active, applies the chosen entry to every selected
  position via the bulk path instead of the single-finding path, reports skipped positions after
  save, and does not alter the current selection when opened (FR-020b) in
  `frontend/src/app/features/patients/tooth-chart/tooth-context-menu.component.spec.ts` (extends
  T054)

### Implementation for User Story 6

- [ ] T116 [US6] Implement `addFindingsBulk(...)` on `ToothFindingService.java` — one `addFinding`
  call per applicable position in one transaction, collects `created`/`skipped` (research.md D6)
  (depends on T112)
- [ ] T117 [P] [US6] Create `ToothFindingBulkCreateRequest.java` and
  `ToothFindingBulkResultResponse.java` per contracts/patient-api.yaml in
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothFindingBulkCreateRequest.java`
  and
  `patient-service/src/main/java/com/dentalclinic/patient/api/ToothFindingBulkResultResponse.java`
- [ ] T118 [US6] Add `POST /patients/{patientId}/tooth-chart/findings/bulk` to
  `ToothFindingController.java` (depends on T116, T117, T113)
- [ ] T119 [US6] Add `addFindingsBulk` method to `tooth-chart.service.ts` (depends on T118)
- [ ] T120 [US6] Add multi-select state, quadrant/arch/segment shortcuts, and drag-select to
  `tooth-chart.component.ts` (depends on T114, T063)
- [ ] T121 [US6] Extend `tooth-context-menu.component.ts` (US1, T065) to detect an active
  multi-selection and, when present, apply the chosen entry to every selected position via
  `addFindingsBulk` (T119) instead of `addFinding`, surfacing any skipped positions after save
  (FR-020b) (depends on T115, T119, T120)
- [ ] T122 [US6] Verify the right-click/long-press handlers already wired in User Story 1 (T066)
  correctly open the same menu when a multi-selection is active, and that opening it never changes
  the current selection (FR-020b) — no new event wiring expected, this is a confirmation/adjustment
  pass (depends on T121)

**Checkpoint**: All six user stories are independently functional.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: RODO export/erasure coverage, append-only-surface verification, a cross-cutting
accessibility audit, and end-to-end validation across every user story.

- [ ] T123 [P] Extend `PatientExportService`/`PatientFullExportResponse` with the `toothChart` field
  — all 52 positions, all canals (including removed), and the full finding history (current,
  resolved, and superseded alike), catalog entries resolved to Polish names (FR-061) in
  `patient-service/src/main/java/com/dentalclinic/patient/rodo/PatientExportService.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/PatientFullExportResponse.java`
  (depends on T055, T097, T107)
- [ ] T124 [P] Confirm `PatientErasureService`'s existing retention-aware erasure procedure covers
  the five new tables by construction (FR-062) — no new erasure endpoint or special-cased logic
  needed, same posture as feature 004's own `TODO(T060)` deferral
- [ ] T125 Verify no `@PatchMapping`/`@DeleteMapping` exists on `ToothFindingController.java`
  (append-only enforced by API surface, FR-030) — grep the file and confirm zero matches
- [ ] T126 [P] Run `specs/005-tooth-chart-diagnoses/quickstart.md` Scenarios 1-8 end-to-end against a
  local stack (Testcontainers Postgres + `backend` + `patient-service` + `frontend`); Scenario 8's
  screen-reader/320px/5-doctor-legend checks are manual and documented as such, not automated (same
  accepted gap plan.md records for SC-004/SC-009, now extended to SC-001/SC-012/SC-013 per session
  2026-08-30 piąta tura)
- [ ] T127 [P] Verify checkstyle/lint and full test suites are green: `cd patient-service && ./gradlew
  build`, `cd backend && ./gradlew build`, `cd frontend && npm run lint && npm test`
- [ ] T128 Document the security/compliance self-review required by constitution v1.5.0's
  risk-tiered gate in the PR description before merge (this PR touches patient data and audit
  logging), explicitly addressing FR-058's ASSISTANT/DOCTOR write-parity divergence from
  `004-patient-medical-history` as a deliberate, spec-driven decision (plan.md Constitution Check);
  do not enable auto-merge on this PR
- [ ] T129 [P] Cross-cutting audit: confirm every diagram visual state introduced across US1-US6
  (active diagnosis, existing-state, missing-tooth, canal treatment states, layer-filtered/dimmed
  markers) carries a non-color cue per FR-050, and every interactive element (tooth, surface zone,
  context-menu item, dentition-mode switch, zoom control) is keyboard-reachable with screen-reader
  text per FR-052/FR-053 — a verification pass over existing implementation, not new UI

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T001→T002 (backend enum must exist
  before patient-service's mirror is updated); T003 independent; T004/T005 independent of T001-T003
  but should land before Phase 2 touches the same directories.
- **Foundational (Phase 2)**: Depends on Phase 1 (schema must exist). BLOCKS all user stories.
- **User Stories (Phase 3-8)**: All depend on Phase 2 completion. Recommended order for solo-
  developer incremental delivery: P1 (US1) → P2 (US2, US3) → P3 (US4, US5, US6), per spec.md
  priorities. Note the reversal of the usual independence assumption for one pair: US6's quick-menu
  task (T121) depends on the component US1 builds (T065) — this is intentional (session 2026-08-30
  piąta tura, G1) since FR-020a's single-tooth path doesn't need multi-select at all.
- **Polish (Phase 9)**: Depends on all six user stories being complete (export/erasure need every
  table populated to be meaningfully tested; T129's audit needs every story's UI to exist).

### Shared-File Sequencing Across Stories

Several files are touched by more than one story and MUST be edited sequentially within that file
even though the stories are otherwise independent:

- `ToothChartController.java`: T042 (Foundational) → T072 (US2) → T096 (US4) → T108 (US5).
- `ToothChartService.java`: T037 (Foundational) → T071 (US2) → T095 (US4) → T107 (US5).
- `ToothFindingController.java`: T057 (US1) → T086 (US3) → T118 (US6).
- `ToothFindingService.java`: T055 (US1) → T084 (US3) → T116 (US6).
- `tooth-chart.service.ts`: T044 (Foundational) → T060 (US1) → T073 (US2) → T087 (US3) → T089 (US3)
  → T100 (US4) → T103 (US4) → T110 (US5) → T119 (US6).
- `tooth-chart.component.ts`: T048 (Foundational) → T063 (US1) → T074/T075/T077/T078/T079 (US2) →
  T089 (US3) → T111 (US5) → T120 (US6).
- `tooth-arch.component.ts`: T047 (Foundational) → T064 (US1) → T076 (US2) → T101/T102 (US4) → T111
  (US5).
- `tooth-detail-panel.component.ts`: T062 (US1) → T076 (US2) → T088 (US3) → T101/T102 (US4).
- `tooth-context-menu.component.ts`: T065 (US1, created here) → T121/T122 (US6, extended here —
  this file is NOT touched by US2-US5).
- `patients.models.ts`: T043 (Foundational), T059 (US1) — additive, safe to parallelize in
  practice, but sequence if working solo to avoid merge friction.

### Within Each User Story

- Tests MUST be written and shown to fail before their corresponding implementation tasks
  (constitution Principle I).
- Entity/enum → Repository → Service → Response/Request records → Controller → Frontend service →
  Frontend component.

### Parallel Opportunities

- All twelve Phase 2 enum tasks (T006-T017) can run in parallel — different files, no dependencies.
- All Foundational test tasks marked [P] (T028-T035) can run in parallel.
- Within each user story, [P]-marked test tasks can run in parallel; DTO-record tasks marked [P]
  can run in parallel with each other (not with the entity/service tasks they depend on).
- Once Phase 2 completes, US1/US2/US3 (P1/P2) and, once those land, US4/US5 (P3) can be worked in
  parallel by different contributors; US6 should follow US1 specifically (not just Phase 2) given
  the T065→T121 dependency above. For a solo developer, sequential priority order is recommended
  regardless.

---

## Parallel Example: Phase 2 Enums

```bash
# Launch all twelve enum creation tasks together:
Task: "Create DentitionMode.java"
Task: "Create DentitionType.java"
Task: "Create ToothType.java"
Task: "Create ToothPresence.java"
Task: "Create RootCanalState.java"
Task: "Create DiagnosisCategory.java"
Task: "Create AnatomicalScope.java"
Task: "Create FindingLayer.java"
Task: "Create ToothSurface.java"
Task: "Create FindingClinicalStatus.java"
Task: "Create FindingRecordStatus.java"
Task: "Create FindingAuthorRole.java"
```

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Failing JUnit test for surface-scope validation in ToothFindingServiceTest.java"
Task: "Failing JUnit API/RBAC test in ToothFindingControllerTest.java"
Task: "Failing Vitest test for tooth-detail-panel.component.ts"
Task: "Failing Vitest test for surface-map.component.ts"
Task: "Failing Vitest test for tooth-context-menu.component.ts (single-tooth path)"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (migrations, cleanup).
2. Complete Phase 2: Foundational (schema, read path, diagram skeleton, diagnosis catalog).
3. Complete Phase 3: User Story 1 (rozpoznanie powierzchniowe, including the quick context-menu) —
   the sole reason this feature exists.
4. **STOP and VALIDATE**: run quickstart.md Scenario 1 independently.
5. Document the PR-time security/compliance self-review (T128) before merging even the MVP slice.

### Incremental Delivery

1. Phase 1 + Phase 2 → Foundation ready.
2. Add US1 (rozpoznanie + quick menu) → validate via quickstart Scenario 1 → merge (MVP).
3. Add US2 (odczyt/legenda/middle-strip surfaces) → validate via Scenario 2 → merge.
4. Add US3 (korekta/zamknięcie + conflict handling) → validate via Scenario 3 → merge.
5. Add US4 (braki zębowe/kanały) → validate via Scenario 4 → merge.
6. Add US5 (uzębienie mleczne/mieszane) → validate via Scenario 5 → merge.
7. Add US6 (zaznaczenie wielokrotne + quick-menu extension) → validate via Scenario 6 → merge.
8. Phase 9 (Polish) → validate Scenarios 7-8 (RBAC/audit, RODO export, manual checks) → final
   merge.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- Every task touching `ToothChartService`/`ToothFindingService`/`RootCanalService`/
  `DiagnosisCatalogService`/their controllers must route through `PatientAuditWriter.append(...)` —
  no new, second audit mechanism (Principle III, research.md D9).
- No `PATCH`/`DELETE` endpoint may ever be added to `ToothFindingController.java` (FR-030) —
  corrections and closures are always a new `POST` with `supersedesFindingId` set (research.md D3).
- `ToothPosition` and `RootCanal` are the only mutable-in-place entities (research.md D4); both
  carry a JPA `@Version` column, and every mutating endpoint on them must accept and check
  `expectedVersion` (research.md D7, FR-070) — surfaced to the user via the single conflict-handler
  built in US3 (T089) and reused in US4 (T103).
- The diagnosis catalog seeds **four** "inne rozpoznanie" rows, one per `AnatomicalScope` — never
  add a fifth or collapse them back to one; `anatomicalScope` always follows the referenced catalog
  entry, with no exception for the free-text fallback (D1, data-model.md).
- `tooth-context-menu.component.ts` is built once, in US1 (T065), for the single-tooth path; US6
  only extends it (T121/T122) — do not recreate it or duplicate its event wiring in US6.
- This PR requires a documented security/compliance self-review before merge and must not have
  auto-merge enabled (constitution v1.5.0 risk-tiered gate, plan.md Constitution Check) — it must
  explicitly address FR-058's ASSISTANT/DOCTOR write-parity divergence from
  `004-patient-medical-history` as deliberate, not an oversight.

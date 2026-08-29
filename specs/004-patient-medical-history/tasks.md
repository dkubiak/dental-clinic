---

description: "Task list template for feature implementation"
---

# Tasks: Historia medyczna pacjenta

**Input**: Design documents from `/specs/004-patient-medical-history/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/ (pointer file; actual
contracts amended in `specs/001-staff-auth-rbac/contracts/rbac-policy.md` and
`specs/002-patient-records/contracts/patient-api.yaml`), quickstart.md

**Tests**: Included — constitution Principle I (Test-First Development) is NON-NEGOTIABLE for this
project; every entity/service/controller/component task below is preceded by a failing test task,
mirroring the existing `tooth-chart` test style (`ToothStateAutoCreationTest`,
`ToothChartApiTest`, `tooth-chart.component.spec.ts`).

**Organization**: Tasks are grouped by user story (P1 alergie, P2 leki, P3 choroby przewlekłe) so
each can be implemented, tested, and demoed independently, per spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: US1 (alergie), US2 (leki), US3 (choroby) — maps to spec.md priorities
- File paths are exact and repo-relative

## Path Conventions

Existing web-application layout (established by 001/002, unchanged):

- `backend/src/main/resources/db/migration/` — auth-service's Flyway history (shared
  `audit_event_type` enum)
- `patient-service/src/main/java/com/dentalclinic/patient/` — patient-service Java sources
- `patient-service/src/test/java/com/dentalclinic/patient/` — patient-service JUnit tests
- `frontend/src/app/features/patients/` — Angular sources
- `specs/001-staff-auth-rbac/contracts/rbac-policy.md`,
  `specs/002-patient-records/contracts/patient-api.yaml` — already amended by `/speckit-plan`
  (research.md #8); no further edits needed unless implementation reveals a contract gap

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Migrations and enum plumbing shared by all three sub-resources (allergies,
medications, chronic conditions) — none of it is specific to one user story.

- [X] T001 Add three values (`MEDICAL_HISTORY_ENTRY_ADDED`, `MEDICAL_HISTORY_ENTRY_VIEWED`,
  `MEDICAL_HISTORY_HISTORY_VIEWED`) to the shared `audit_event_type` Postgres enum in
  `backend/src/main/resources/db/migration/V13__audit_event_type_medical_history.sql`
  (data-model.md, research.md #2 — mirrors `V11__audit_event_type_patient.sql`'s `ALTER TYPE ...
  ADD VALUE` style)
- [X] T002 Add the three new values to `patient-service`'s own enum mirror in
  `patient-service/src/main/java/com/dentalclinic/patient/audit/PatientAuditEventType.java`
- [X] T003 Create `patient-service/src/main/resources/db/migration/V3__medical_history.sql`: two
  new Postgres enums (`medical_history_record_status` [`CURRENT`, `SUPERSEDED`],
  `allergy_severity` [`CRITICAL`, `MODERATE`], `chronic_condition_status` [`ACTIVE`, `PAST`]) and
  three tables — `allergy_entry`, `medication_entry`, `chronic_condition_entry` — each per the
  exact column list in data-model.md (id, patient_record_id FK → patient_record(id), domain
  columns, record_status DEFAULT 'CURRENT', supersedes_entry_id nullable self-FK, created_at,
  created_by)
- [X] T004 [P] Create `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/RecordStatus.java`
  (enum `CURRENT`, `SUPERSEDED`) — shared by all three entities below

**Checkpoint**: Schema and enums exist; no application code depends on them yet.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Nothing in this feature has a cross-story blocking dependency beyond Phase 1 — each
of the three entity types (allergy/medication/chronic-condition) is structurally independent
(separate table, separate repository, separate controller path), matching spec.md's explicit
"Independent Test" claim for all three user stories. No additional foundational phase is needed;
proceed directly to Phase 3.

**Checkpoint**: Foundation ready — user story implementation can begin.

---

## Phase 3: User Story 1 - Lekarz przegląda i odnotowuje alergie pacjenta (Priority: P1) 🎯 MVP

**Goal**: DOCTOR can add/view allergy entries with severity; a CRITICAL entry is visually flagged
via `app-status-indicator` on the patient-detail screen without scrolling; ASSISTANT has read
parity (current + "historia zmian"); RECEPTION sees only the fact-only `hasCriticalAllergyAlert`
boolean; every read/write is audit-logged; corrections are append-only (FR-010).

**Independent Test**: Log in as DOCTOR, open a patient with a CRITICAL allergy, verify the
status-indicator is visible on first screen with no scroll/click; log in as RECEPTION and verify
`GET /patients/{id}/allergies` returns 404 while `hasCriticalAllergyAlert: true` shows in the
basic-data response — all without touching medications or chronic conditions.

### Tests for User Story 1 ⚠️

- [X] T005 [P] [US1] Failing JUnit test for append-only correction behavior (insert with
  `supersedesEntryId` flips prior row to `SUPERSEDED` in the same transaction; no in-place mutation
  possible); and that blank `substance`/`reactionType` is rejected while arbitrary free text (no
  dictionary check) is accepted (FR-011) in
  `patient-service/src/test/java/com/dentalclinic/patient/medicalhistory/AllergyEntryServiceTest.java`
- [X] T006 [P] [US1] Failing JUnit test for `hasCriticalAllergyAlert` computed field (`EXISTS`
  query true only when a `CURRENT` + `CRITICAL` row exists) in
  `patient-service/src/test/java/com/dentalclinic/patient/medicalhistory/CriticalAllergyAlertTest.java`
- [X] T007 [US1] Failing JUnit API/RBAC test in
  `patient-service/src/test/java/com/dentalclinic/patient/api/MedicalHistoryControllerTest.java`
  covering all of: DOCTOR `POST`/`GET /allergies`/`GET /allergies/history` succeed; ASSISTANT
  `GET` succeeds, `POST` returns 404; RECEPTION `GET /allergies` returns 404;
  `GET /patients/{id}` (basic data) exposes `hasCriticalAllergyAlert` to RECEPTION; default
  `GET /allergies` excludes `SUPERSEDED` rows while `GET /allergies/history` includes them; a
  patient with zero allergy entries returns `200` with an empty array, not an error (FR-012); each
  operation produces the expected `audit_log_entry` row (`MEDICAL_HISTORY_ENTRY_ADDED` /
  `_VIEWED` / `_HISTORY_VIEWED`, `metadata.entryType = ALLERGY`) — mirrors
  `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartApiTest.java`'s structure
- [X] T008 [P] [US1] Failing Vitest test for `medical-history.service.ts`'s allergy methods
  (`getAllergies`, `getAllergyHistory`, `addAllergy`) in
  `frontend/src/app/features/patients/medical-history/medical-history.service.spec.ts`
- [X] T009 [P] [US1] Failing Vitest test for `medical-history.component.ts` covering: empty state
  ("brak odnotowanych alergii"), CRITICAL entry rendered via `app-status-indicator`, add-entry form
  visible only for DOCTOR, "Historia zmian" panel toggling supersede-linked entries, in
  `frontend/src/app/features/patients/medical-history/medical-history.component.spec.ts`
- [X] T010 [P] [US1] Failing Vitest test for the `hasCriticalAllergyAlert` badge rendering in the
  patient-detail header (visible to RECEPTION without opening the "Historia medyczna" tab) in
  `frontend/src/app/features/patients/patient-detail/patient-detail.component.spec.ts`

### Implementation for User Story 1

- [X] T011 [US1] Create `AllergyEntry` JPA entity (fields per data-model.md: substance,
  reactionType, severity, recordStatus, supersedesEntryId, createdAt, createdBy) in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/AllergyEntry.java`
- [X] T012 [P] [US1] Create `AllergySeverity` enum (`CRITICAL`, `MODERATE`) in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/AllergySeverity.java`
- [X] T013 [US1] Create `AllergyEntryRepository` (Spring Data JPA — `findByPatientRecordIdAndRecordStatus`,
  `findByPatientRecordId` for history) in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/AllergyEntryRepository.java`
  (depends on T011)
- [X] T014 [US1] Create `MedicalHistoryService` with allergy methods — `getCurrentAllergies`,
  `getAllergyHistory`, `addAllergy` (append-only correction: insert new `CURRENT` row, if
  `supersedesEntryId` present flip that row to `SUPERSEDED` in the same `@Transactional` method),
  `hasCriticalAllergyAlert` — in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/MedicalHistoryService.java`
  (depends on T013; calls `PatientAuditWriter.append` for every op per data-model.md's audit
  metadata shape)
- [X] T015 [P] [US1] Create `AllergyEntryResponse` record (mirrors `ToothStateResponse.from(...)`
  static-factory pattern) and `AllergyCreateRequest` record per contracts/patient-api.yaml schemas
  in `patient-service/src/main/java/com/dentalclinic/patient/api/AllergyEntryResponse.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/AllergyCreateRequest.java`
- [X] T016 [US1] Create `MedicalHistoryController` with
  `GET/POST /patients/{patientId}/allergies` and `GET /patients/{patientId}/allergies/history`,
  `@PreAuthorize("hasAnyRole('DOCTOR','ASSISTANT')")` on GETs and
  `@PreAuthorize("hasRole('DOCTOR')")` on POST, deny→404 per rbac-policy.md rule 2, in
  `patient-service/src/main/java/com/dentalclinic/patient/api/MedicalHistoryController.java`
  (depends on T014, T015; mirrors `ToothChartController.java`'s structure exactly)
- [X] T017 [US1] Add `hasCriticalAllergyAlert` field to `PatientDetailResponse` in
  `patient-service/src/main/java/com/dentalclinic/patient/api/PatientDetailResponse.java`,
  populated from `MedicalHistoryService.hasCriticalAllergyAlert` wherever `PatientDetailResponse`
  is constructed (depends on T014)
- [X] T018 [P] [US1] Add `AllergyEntry`, `RecordStatus`, `Severity` TypeScript types to
  `frontend/src/app/features/patients/patients.models.ts`
- [X] T019 [US1] Create `medical-history.service.ts` with `getAllergies`, `getAllergyHistory`,
  `addAllergy` HTTP methods (thin-relay pattern, mirrors `patients.service.ts`'s tooth-chart
  methods) in `frontend/src/app/features/patients/medical-history/medical-history.service.ts`
  (depends on T018)
- [X] T020 [US1] Create `medical-history.component.ts` (standalone, inline `styles:`) rendering the
  allergy section: current-entries list, `app-status-indicator` (`type="error"`) on CRITICAL
  entries, empty state, expandable "Historia zmian" panel, DOCTOR-only add-entry form, in
  `frontend/src/app/features/patients/medical-history/medical-history.component.ts` (depends on
  T019)
- [X] T021 [US1] Wire `medical-history.component` into `patient-detail.component.ts` as a fourth
  `mat-tab` ("Historia medyczna") gated by a `canViewMedicalHistory` computed signal (`DOCTOR` or
  `ASSISTANT`, mirrors `canViewToothChart`), and render the `hasCriticalAllergyAlert` badge via
  `app-status-indicator` in the header outside any tab (visible to RECEPTION) in
  `frontend/src/app/features/patients/patient-detail/patient-detail.component.ts` (depends on
  T020, T017) — this is what unblocks `specs/003-brand-ui-theme/tasks.md` T049
- [X] T022 [US1] Extend `PatientExportService` to include `allergies` (full history — current +
  superseded, research.md #6) in the export payload, and add the corresponding field to
  `PatientFullExportResponse`, in
  `patient-service/src/main/java/com/dentalclinic/patient/rodo/PatientExportService.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/PatientFullExportResponse.java`
  (depends on T014)

**Checkpoint**: User Story 1 (alergie) is fully functional and independently testable — MVP scope.

---

## Phase 4: User Story 2 - Lekarz przegląda przyjmowane leki pacjenta (Priority: P2)

**Goal**: DOCTOR can add/view medication entries (name, dosage, start date); ASSISTANT has read
parity; RECEPTION has no access; audit-logged; append-only corrections — same mechanism as US1,
applied to a second, structurally independent table.

**Independent Test**: Log in as DOCTOR, add a medication entry to a patient, verify it appears
with its start date and is audit-logged — without any allergy or chronic-condition data present.

### Tests for User Story 2 ⚠️

- [X] T023 [P] [US2] Failing JUnit test for medication append-only correction behavior; and that
  blank `name`/`dosage` is rejected while arbitrary free text (no dictionary check) is accepted
  (FR-011) in
  `patient-service/src/test/java/com/dentalclinic/patient/medicalhistory/MedicationEntryServiceTest.java`
- [X] T024 [US2] Failing JUnit API/RBAC test extending `MedicalHistoryControllerTest.java` (T007)
  with medication cases: DOCTOR add/view, ASSISTANT read-only, RECEPTION 404, current-vs-history
  split, a patient with zero medication entries returns `200` with an empty array (FR-012), audit
  rows with `metadata.entryType = MEDICATION`
- [X] T025 [P] [US2] Failing Vitest test for `medical-history.service.ts`'s medication methods
  (`getMedications`, `getMedicationHistory`, `addMedication`) in
  `frontend/src/app/features/patients/medical-history/medical-history.service.spec.ts`
- [X] T026 [P] [US2] Failing Vitest test extending `medical-history.component.spec.ts` (T009) with
  the medication section: empty state ("brak odnotowanych leków"), list with start date, add form

### Implementation for User Story 2

- [X] T027 [US2] Create `MedicationEntry` JPA entity (name, dosage, startDate, recordStatus,
  supersedesEntryId, createdAt, createdBy) in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/MedicationEntry.java`
- [X] T028 [US2] Create `MedicationEntryRepository` in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/MedicationEntryRepository.java`
  (depends on T027)
- [X] T029 [US2] Add medication methods (`getCurrentMedications`, `getMedicationHistory`,
  `addMedication`) to `MedicalHistoryService.java` (depends on T028, T014 — same class as US1's
  allergy methods, per research.md #7's single-service decision)
- [X] T030 [P] [US2] Create `MedicationEntryResponse` and `MedicationCreateRequest` records in
  `patient-service/src/main/java/com/dentalclinic/patient/api/MedicationEntryResponse.java` and
  `patient-service/src/main/java/com/dentalclinic/patient/api/MedicationCreateRequest.java`
- [X] T031 [US2] Add `GET/POST /patients/{patientId}/medications` and
  `GET /patients/{patientId}/medications/history` endpoints to `MedicalHistoryController.java`
  with the same `@PreAuthorize` rules as allergies (depends on T029, T030)
- [X] T032 [P] [US2] Add `MedicationEntry` TypeScript type to `patients.models.ts`
- [X] T033 [US2] Add `getMedications`, `getMedicationHistory`, `addMedication` methods to
  `medical-history.service.ts` (depends on T032)
- [X] T034 [US2] Add the medications section (list, empty state, "Historia zmian", DOCTOR-only add
  form) to `medical-history.component.ts` (depends on T033)
- [X] T035 [US2] Extend `PatientExportService`/`PatientFullExportResponse` to include `medications`
  (full history) (depends on T029)

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 5: User Story 3 - Lekarz przegląda choroby przewlekłe/przebyte pacjenta (Priority: P3)

**Goal**: DOCTOR can add/view chronic-condition entries (name, clinical status ACTIVE/PAST,
diagnosis date) with `clinicalStatus` kept independent of the `recordStatus` correction flag
(Clarifications Q1); ASSISTANT read parity; RECEPTION no access; audit-logged; append-only.

**Independent Test**: Log in as DOCTOR, add a chronic-condition entry, submit a correction that
only flips `clinicalStatus` (ACTIVE→PAST) via a new `supersedesEntryId`-linked entry, and verify
the old entry's `recordStatus` becomes `SUPERSEDED` while `clinicalStatus` and `recordStatus` vary
independently — without allergy or medication data present.

### Tests for User Story 3 ⚠️

- [ ] T036 [P] [US3] Failing JUnit test proving `clinicalStatus` and `recordStatus` are independent
  state machines on the same entity (a correction can flip `clinicalStatus` alone, or neither, or
  both); and that blank `name` is rejected while arbitrary free text (no dictionary check) is
  accepted (FR-011) in
  `patient-service/src/test/java/com/dentalclinic/patient/medicalhistory/ChronicConditionEntryServiceTest.java`
- [ ] T037 [US3] Failing JUnit API/RBAC test extending `MedicalHistoryControllerTest.java` (T007)
  with chronic-condition cases: DOCTOR add/view, ASSISTANT read-only, RECEPTION 404,
  current-vs-history split, a patient with zero chronic-condition entries returns `200` with an
  empty array (FR-012), audit rows with `metadata.entryType = CHRONIC_CONDITION`
- [ ] T038 [P] [US3] Failing Vitest test for `medical-history.service.ts`'s chronic-condition
  methods (`getChronicConditions`, `getChronicConditionHistory`, `addChronicCondition`) in
  `frontend/src/app/features/patients/medical-history/medical-history.service.spec.ts`
- [ ] T039 [P] [US3] Failing Vitest test extending `medical-history.component.spec.ts` (T009) with
  the chronic-conditions section: empty state ("brak odnotowanych chorób"), list with clinical
  status and diagnosis date, add form

### Implementation for User Story 3

- [ ] T040 [US3] Create `ChronicConditionEntry` JPA entity (name, clinicalStatus, diagnosisDate,
  recordStatus, supersedesEntryId, createdAt, createdBy) in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/ChronicConditionEntry.java`
- [ ] T041 [P] [US3] Create `ChronicConditionStatus` enum (`ACTIVE`, `PAST`) in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/ChronicConditionStatus.java`
- [ ] T042 [US3] Create `ChronicConditionEntryRepository` in
  `patient-service/src/main/java/com/dentalclinic/patient/medicalhistory/ChronicConditionEntryRepository.java`
  (depends on T040)
- [ ] T043 [US3] Add chronic-condition methods (`getCurrentChronicConditions`,
  `getChronicConditionHistory`, `addChronicCondition`) to `MedicalHistoryService.java` (depends on
  T042, T014)
- [ ] T044 [P] [US3] Create `ChronicConditionEntryResponse` and `ChronicConditionCreateRequest`
  records in
  `patient-service/src/main/java/com/dentalclinic/patient/api/ChronicConditionEntryResponse.java`
  and
  `patient-service/src/main/java/com/dentalclinic/patient/api/ChronicConditionCreateRequest.java`
- [ ] T045 [US3] Add `GET/POST /patients/{patientId}/chronic-conditions` and
  `GET /patients/{patientId}/chronic-conditions/history` endpoints to
  `MedicalHistoryController.java` with the same `@PreAuthorize` rules (depends on T043, T044)
- [ ] T046 [P] [US3] Add `ChronicConditionEntry` TypeScript type to `patients.models.ts`
- [ ] T047 [US3] Add `getChronicConditions`, `getChronicConditionHistory`,
  `addChronicCondition` methods to `medical-history.service.ts` (depends on T046)
- [ ] T048 [US3] Add the chronic-conditions section (list, empty state, "Historia zmian",
  DOCTOR-only add form) to `medical-history.component.ts` (depends on T047)
- [ ] T049 [US3] Extend `PatientExportService`/`PatientFullExportResponse` to include
  `chronicConditions` (full history) (depends on T043)

**Checkpoint**: All three user stories are independently functional; "Historia medyczna" tab is
complete.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Feature-wide correctness and compliance checks that cut across all three sub-resources.

- [ ] T050 [P] Run `specs/004-patient-medical-history/quickstart.md` Scenarios 1–6 end-to-end
  against a local stack (Testcontainers Postgres + `backend` + `patient-service` + `frontend`) and
  record results
- [ ] T051 [P] Verify `checkstyle`/lint pass: `cd patient-service && ./gradlew build` and
  `cd frontend && npm run lint`
- [ ] T052 Verify no `PATCH`/`DELETE` mapping exists anywhere on
  `/patients/{patientId}/{allergies,medications,chronic-conditions}` (append-only enforced by API
  surface, FR-010, quickstart.md Scenario 2 step 4) — grep
  `MedicalHistoryController.java` for `@PatchMapping`/`@DeleteMapping` and confirm zero matches
- [ ] T053 Document the security/compliance self-review required by constitution v1.5.0's
  risk-tiered gate (this PR touches patient data and audit logging) in the PR description before
  merge; do not enable auto-merge on this PR (plan.md Constitution Check, "ACTION REQUIRED AT PR
  TIME")

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately. T001→T002 (backend enum must exist
  before patient-service's Java mirror is updated, though they're separate files/modules); T003
  independent of T001/T002; T004 independent.
- **Foundational (Phase 2)**: Empty — no cross-story blocking work beyond Phase 1 (see Phase 2
  note above).
- **User Stories (Phase 3, 4, 5)**: All depend on Phase 1 completion (migrations + enums must
  exist). The three stories touch disjoint tables/files (`AllergyEntry` vs `MedicationEntry` vs
  `ChronicConditionEntry`, separate response/request record files) except where noted below, so
  they can proceed in parallel or in priority order (P1 → P2 → P3, recommended for solo-developer
  incremental delivery per plan.md).
- **Polish (Phase 6)**: Depends on all three user stories being complete.

### Shared-File Sequencing Within Stories

Three files are touched by more than one story and MUST be edited sequentially within that file
(even though the stories are otherwise independent):

- `MedicalHistoryController.java`: T016 (US1) → T031 (US2) → T045 (US3) — each adds a new set of
  endpoints without touching the others'.
- `MedicalHistoryService.java`: T014 (US1) → T029 (US2) → T043 (US3) — each adds a new set of
  methods.
- `medical-history.component.ts` / `.service.ts`: T019/T020 (US1) → T033/T034 (US2) →
  T047/T048 (US3) — each adds a new section.
- `patients.models.ts`: T018 (US1), T032 (US2), T046 (US3) — additive type declarations, safe to
  parallelize in practice despite the shared file (no overlapping lines), but sequence them if
  working solo to avoid merge friction.
- `PatientExportService.java` / `PatientFullExportResponse.java`: T022 (US1) → T035 (US2) → T049
  (US3).

### Within Each User Story

- Tests (T005–T010 / T023–T026 / T036–T039) MUST be written and shown to fail before their
  corresponding implementation tasks (constitution Principle I).
- Entity → Repository → Service → Response/Request records → Controller → Frontend service →
  Frontend component → patient-detail wiring / export wiring.

### Parallel Opportunities

- T004, and the [P] test tasks within each story's test block, can run in parallel (different
  files).
- T012 (AllergySeverity), T015 (Allergy response/request records) — parallel with each other, not
  with T011/T013/T014 (dependency chain).
- Once Phase 1 completes, Phase 3/4/5 can be worked in parallel by different contributors; for a
  solo developer, sequential P1→P2→P3 delivery is recommended (plan.md, quickstart.md ordering).

---

## Parallel Example: User Story 1

```bash
# Launch all tests for User Story 1 together:
Task: "Failing JUnit test for allergy append-only correction in AllergyEntryServiceTest.java"
Task: "Failing JUnit test for hasCriticalAllergyAlert in CriticalAllergyAlertTest.java"
Task: "Failing Vitest test for medical-history.service.ts allergy methods"
Task: "Failing Vitest test for medical-history.component.ts allergy section"
Task: "Failing Vitest test for hasCriticalAllergyAlert badge in patient-detail.component.spec.ts"

# Launch independent-file implementation tasks together:
Task: "Create AllergySeverity enum"
Task: "Create AllergyEntryResponse / AllergyCreateRequest records"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (migrations, shared enums).
2. Phase 2 is empty — proceed directly to Phase 3.
3. Complete Phase 3: User Story 1 (alergie) — this alone unblocks
   `specs/003-brand-ui-theme/tasks.md` T049 and satisfies the highest clinical-risk requirement.
4. **STOP and VALIDATE**: run quickstart.md Scenario 1 + 2 independently.
5. Document the PR-time security/compliance self-review (T053) before merging even the MVP slice,
   since it touches patient data and audit logging regardless of scope.

### Incremental Delivery

1. Phase 1 → Foundation ready (schema + enums).
2. Add User Story 1 (alergie) → validate via quickstart Scenarios 1–2 → merge (MVP).
3. Add User Story 2 (leki) → validate via quickstart Scenario 3 → merge.
4. Add User Story 3 (choroby) → validate via quickstart Scenario 4 → merge.
5. Phase 6 (Polish) → validate quickstart Scenarios 5–6 (RODO export, audit-trail integrity) → final
  merge.

---

## Notes

- [P] tasks = different files, no dependencies.
- [Story] label maps task to specific user story for traceability.
- No entity in this feature has a cross-story dependency — each of the three tables, controllers
  section, and component sections is additive to shared files rather than interdependent.
- Verify tests fail before implementing (Red-Green-Refactor, constitution Principle I).
- Every task touching `MedicalHistoryService`/`MedicalHistoryController` must route through
  `PatientAuditWriter.append(...)` — no new, second audit mechanism (Principle III, research.md
  #2).
- No `PATCH`/`DELETE` endpoint may ever be added for these three resources (FR-010) — corrections
  are always a new `POST` with `supersedesEntryId` set.
- FR-008 (audit-log immutability) is inherited from feature 001's existing SELECT/INSERT-only
  grant on `audit_log_entry` — not independently re-tested per new event type in this feature,
  since it's the same table and the same grant regardless of `audit_event_type` value.
- This PR requires a documented security/compliance self-review before merge and must not have
  auto-merge enabled (constitution v1.5.0 risk-tiered gate, plan.md Constitution Check).

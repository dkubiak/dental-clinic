---

description: "Task list for Kartoteka pacjentów (dane podstawowe i stan uzębienia)"
---

# Tasks: Kartoteka pacjentów (dane podstawowe i stan uzębienia)

**Input**: Design documents from `/specs/002-patient-records/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/patient-api.yaml, quickstart.md

**Tests**: Included and REQUIRED — constitution.md Principle I (Test-First Development,
NON-NEGOTIABLE) and plan.md's Constitution Check both mandate a failing test before each
implementation task (Red-Green-Refactor).

**Organization**: Tasks are grouped by user story (spec.md's US1/US2/US3) to enable independent
implementation and testing. This feature spans **two services** — `backend/` (`auth-service`,
modified) and the new `patient-service/` (own top-level Gradle project) — plus `frontend/`; every
task states which one it touches.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: US1, US2, or US3 (spec.md) — omitted for Setup/Foundational/Polish tasks
- Exact file paths are included in every description

---

## Phase 1: Setup

**Purpose**: Scaffold the new `patient-service` deployable and its delivery-pipeline wiring. No
business logic in this phase.

- [X] T001 Create the `patient-service/` top-level Gradle project: `settings.gradle.kts`
      (`rootProject.name = "patient-service"`), `build.gradle.kts` mirroring
      `backend/build.gradle.kts`'s Spring Boot 4.1.x / Spring Security 7 / Spring Data JPA /
      Flyway / `spring-session-jdbc` / Testcontainers dependencies (no AWS SDK, no `java-totp` —
      not needed by this service), `checkstyle.xml`, and the Gradle wrapper (`gradlew`/
      `gradlew.bat`/`gradle/`), all under `patient-service/`. Also includes the minimal bootable
      skeleton needed for "buildable, deployable" to be true: `PatientServiceApplication.java`
      (`@SpringBootApplication` main class) and `application.yml` — mirrors
      `backend/src/main/java/.../AuthServiceApplication.java` (no `@EnableScheduling`, no
      scheduled jobs in this feature). Verified: `./gradlew compileJava` succeeds.
- [X] T002 [P] Create `patient-service/Dockerfile` (multi-stage JDK 25 build + JRE runtime,
      mirroring `backend/Dockerfile`) and `patient-service/.dockerignore`
- [X] T003 [P] Create the `helm/patient-service/` chart mirroring `helm/auth-service/`:
      `Chart.yaml` (own description referencing this feature's Risk Tier decision),
      `values.yaml` (`replicaCount: 2`, `autoscaling.minReplicas: 2`/`maxReplicas: 6`),
      `templates/{_helpers.tpl,deployment.yaml,service.yaml,hpa.yaml,serviceaccount.yaml}`
- [X] T004 [P] Add a `patient-service` service block to `docker-compose.yml` (build context
      `./patient-service`, its own port, `DB_USERNAME=patient_service_app`, `depends_on:
      postgres`), per the file's own "every new backend service gets its own service block"
      convention. Also adds `docker/postgres/init/03-create-patient-service-role.sh` (mirrors
      `01-create-app-role.sh`) and `PATIENT_SERVICE_APP_PASSWORD` on the `postgres` service,
      since the role needs a local-dev password pre-created before either service's Flyway runs.
- [X] T005 [P] Add a `patient-service` job to `.github/workflows/ci.yml`, mirroring the existing
      `backend` job (JDK 25 setup, `./gradlew build --no-daemon`, `working-directory:
      patient-service`, test-report upload on failure). Also added `patient-service` to
      `frontend-e2e`'s `needs:` list.

**Checkpoint**: `patient-service` exists as an empty, buildable, deployable skeleton.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Cross-service infrastructure every user story depends on — RBAC role extension,
audit-log extension and concurrency fix, cross-service session validation, the frontend shell, and
the base `PatientRecord` entity.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

### `auth-service` RBAC + audit extension (modifies already-shipped 001 code)

- [X] T006 `auth-service` migration `backend/src/main/resources/db/migration/V9__staff_role_assistant.sql`:
      `ALTER TYPE staff_role ADD VALUE 'ASSISTANT'` (research.md #4; own migration/transaction so
      it commits before any later migration/code references the value)
- [X] T007 [P] Update `backend/src/main/java/com/dentalclinic/auth/role/Role.java`: add
      `ASSISTANT` (depends on T006)
- [X] T008 [P] Update `frontend/src/app/core/auth/role.guard.ts` and the `core/rbac` UI-visibility
      helpers to recognize `ASSISTANT`; add an `/assistant` route with
      `canMatch: [roleGuard(['ASSISTANT'])]` in `frontend/src/app/app.routes.ts`. No standalone
      `core/rbac` directory actually exists (plan.md's Project Structure was aspirational) — the
      real UI-visibility helpers are `auth-state.ts` (`StaffRole` type), `role-home-route.ts`, and
      `accounts.component.ts`'s role dropdown (`ROLES` constant, needed so admins can actually
      assign `ASSISTANT` via the UI). `role.guard.ts` itself needed no change (role-parametric
      already). Verified: `npm run lint` and `npm test` (24/24) pass.
- [X] T009 `auth-service` migration `backend/src/main/resources/db/migration/V10__patient_service_role.sql`:
      idempotently `CREATE ROLE patient_service_app` (mirrors the `DO $$ ... IF NOT EXISTS`
      pattern already used for `auth_service_app`/`auth_service_retention` in `V5__audit_log.sql`/
      `V8__audit_log_retention_role.sql`)
- [X] T009a [P] Update `specs/001-staff-auth-rbac/contracts/rbac-policy.md`: add the `ASSISTANT`
      role row and the feature-002 permission-matrix rows (create/edit basic data, read basic
      data, tooth chart, visit-history placeholder, export/erasure), plus enforcement rule 6
      (`ADMINISTRATOR` clinical-data exclusion extends to patient records) — tracked here for
      traceability even though this is a cross-feature (001-owned) file, per the constitution's
      phase-end commit discipline
- [X] T010 `auth-service` migration `backend/src/main/resources/db/migration/V11__audit_event_type_patient.sql`:
      `ALTER TYPE audit_event_type ADD VALUE` for `PATIENT_RECORD_CREATED`,
      `PATIENT_RECORD_UPDATED`, `PATIENT_RECORD_VIEWED`, `TOOTH_STATE_CHANGED`,
      `TOOTH_CHART_VIEWED`, `PATIENT_DATA_EXPORTED`, `PATIENT_DATA_ERASURE_REQUESTED`,
      `PATIENT_DATA_ERASURE_COMPLETED` (data-model.md; `PATIENT_RECORD_VIEWED`/`TOOTH_CHART_VIEWED`
      added for FR-007/SC-003 read-audit coverage)
- [X] T011 `auth-service` migration `backend/src/main/resources/db/migration/V12__audit_log_entry_patient_target.sql`:
      `ALTER TABLE audit_log_entry ADD COLUMN target_patient_record_id UUID` (nullable, **no FK**
      — research.md #5); `GRANT SELECT, INSERT ON audit_log_entry TO patient_service_app` plus a
      separate `GRANT USAGE, SELECT ON SEQUENCE audit_log_entry_id_seq TO patient_service_app`
      (sequences take `USAGE`/`SELECT`, not `INSERT` — mirror `V5__audit_log.sql`'s two-statement
      grant exactly, do not combine table and sequence into one `GRANT ... ON a, b` list) and
      `GRANT SELECT, UPDATE ON spring_session, spring_session_attributes` to
      `patient_service_app` (depends on T009)

### Audit hash-chain concurrency fix (research.md #5a)

- [X] T012 [P] Write a failing Testcontainers test
      `backend/src/test/java/com/dentalclinic/auth/auditlog/AuditLogWriterConcurrencyTest.java`:
      instantiate **two separate** `AuditLogWriter` objects sharing one Testcontainers Postgres
      instance (simulating two writers — today's `synchronized` only serializes calls within one
      JVM object), fire concurrent `append()` calls from both, and assert the resulting chain
      segment is unbroken — this MUST fail against the current implementation. "Fail" here is a
      compile error against the old 1-arg constructor (the fix's explicit-transaction design
      necessarily changes the constructor signature) rather than a runtime race — verified by
      temporarily stashing the T013 fix and confirming non-compilation, then restoring it.
      Verifies only the entries this test itself wrote (not `AuditHashChainVerifier.verifyAll()`
      against the whole shared-Postgres-container table), since `AuditLogRetentionJobTest`
      legitimately deletes an old row elsewhere in that shared chain.
- [X] T013 Fix `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditLogWriter.java`: replace
      `synchronized` + `@Transactional` with an explicit `TransactionTemplate`-demarcated
      transaction holding `pg_advisory_xact_lock(<fixed key>)` around "read chain tail → compute
      hash → insert" (via `JdbcTemplate`, participating in the same transaction through Spring's
      `DataSource`-synchronized `JpaTransactionManager`) — makes T012 pass (depends on T012).
      Verified: full `./gradlew build` (65 tests + checkstyle + spotless) passes.

### Frontend shell (needed before any patient-facing screen)

- [X] T014 [P] Write a failing Vitest test
      `frontend/src/app/core/shell/app-shell.component.spec.ts`: renders a persistent toolbar,
      role-aware nav, and a "Nowy pacjent" FAB/toolbar button (shown for RECEPTION/DOCTOR per
      FR-001, hidden for ASSISTANT). Verified failing (module-not-found) before T015.
- [X] T015 Implement `frontend/src/app/core/shell/app-shell.component.ts`: mobile-first persistent
      toolbar/nav (bottom-nav below 768px, expanded toolbar links at/above it — CSS breakpoint,
      no separate desktop component) wrapping `<router-outlet>`, with the "Nowy pacjent" primary
      action (FAB on mobile, toolbar button on desktop) — makes T014 pass (5/5).
- [X] T016 Update `frontend/src/app/app.routes.ts`: nest the `RECEPTION`/`DOCTOR`/`ASSISTANT`
      role routes under `AppShellComponent`, replacing `RoleHomeComponent`'s placeholder body
      (depends on T008, T015). Each child route keeps its own precise per-role `canMatch` (not
      loosened by the wrapping); `ADMINISTRATOR` deliberately stays outside this shell (separate
      tier, no clinical-data access). Default landing route → patient search is **not yet wired**
      — children still render `RoleHomeComponent`; `features/patients` (US1, T038-T040) replaces
      that placeholder body once the real patient-search component exists. Verified: `npm run
      lint`, `npm test` (24/24), and `npm run build` all pass.

### `patient-service` schema, cross-service session auth, and base entity

- [X] T017 Create `patient-service` Flyway migration
      `patient-service/src/main/resources/db/migration/V1__patient_record.sql`: `CREATE TABLE
      patient_record` (fields per data-model.md: id, first_name, last_name, date_of_birth, pesel,
      address_street/building_no/postal_code/city, created_at/by, updated_at/by), partial unique
      index on `pesel WHERE pesel IS NOT NULL`, btree index on `lower(last_name)`; idempotently
      `CREATE ROLE patient_service_app` (belt-and-suspenders with T009 — research.md #7); `GRANT
      SELECT, INSERT, UPDATE, DELETE ON patient_record TO patient_service_app` — `id` is a UUID
      PK, no sequence grant
- [X] T018 Create `patient-service` Flyway migration
      `patient-service/src/main/resources/db/migration/V2__tooth_state.sql`: `CREATE TABLE
      tooth_state` (id, patient_record_id FK → patient_record.id, tooth_number, status enum
      `HEALTHY`/`SICK` default `HEALTHY`, updated_at, updated_by), unique index on
      `(patient_record_id, tooth_number)`, index on `patient_record_id`; `GRANT SELECT, INSERT,
      UPDATE, DELETE ON tooth_state TO patient_service_app` (depends on T017)
- [X] T019 [P] Write a failing integration test suite:
      `patient-service/src/test/java/com/dentalclinic/patient/PostgresIntegrationTestBase.java`
      (Testcontainers base class, mirroring `auth-service`'s own) and
      `patient-service/src/test/java/com/dentalclinic/patient/session/SessionAuthenticationFilterTest.java`:
      a pre-seeded `spring_session`/`spring_session_attributes` row for a `DOCTOR` principal lets
      a protected endpoint succeed (a test-only `/test-support/authenticated` controller, mirroring
      auth-service's `TestOnlyAdminController` pattern); no/invalid/expired session ⇒ `401`. Since
      auth-service's session tables aren't created by patient-service's own migrations, the test
      creates a structural copy of them itself (mirroring V3__session.sql) after Flyway runs.
      Verified failing (compile error — filter/config classes didn't exist) before T020.
- [X] T020 Implement
      `patient-service/src/main/java/com/dentalclinic/patient/session/{SessionAuthenticationFilter.java,SecurityConfig.java}`:
      reads the `SESSION` cookie, looks up `spring_session`/`spring_session_attributes` via
      `JdbcTemplate`, deserializes the same JDK-serialized Spring Security `SecurityContext`
      auth-service writes, populates `SecurityContextHolder`, bumps `last_access_time`. Security
      chain is `SessionCreationPolicy.STATELESS` (this service never creates its own `HttpSession`)
      with CSRF wired via the same `CookieCsrfTokenRepository` auth-service uses (same-origin
      XSRF-TOKEN cookie covers both services). `/actuator/health/**` is `permitAll` for k8s probes
      — note: `spring-boot-starter-actuator` itself isn't yet a dependency of either service
      (pre-existing gap carried over from 001, not introduced here). Makes T019 pass (4/4)
      (depends on T011, T017).
- [X] T021 [P] Create `patient-service/src/main/java/com/dentalclinic/patient/session/StaffRole.java`
      (`RECEPTION`, `DOCTOR`, `ASSISTANT`, `ADMINISTRATOR`) — intentionally duplicated from
      `auth-service`'s `Role` enum, not shared code (plan.md — no shared library exists between
      the two services yet)
- [X] T022 [P] Write a failing test
      `patient-service/src/test/java/com/dentalclinic/patient/record/PatientRecordRepositoryTest.java`:
      save/find round-trip; the partial-unique-PESEL constraint is enforced at the DB level
      (`DataIntegrityViolationException`)
- [X] T023 Implement
      `patient-service/src/main/java/com/dentalclinic/patient/record/{PatientRecord.java,PatientRecordRepository.java}` —
      makes T022 pass (4/4) (depends on T017). `id` is application-generated (`UUID.randomUUID()`
      at construction), matching `StaffAccount`'s precedent in auth-service rather than relying on
      the DB's `gen_random_uuid()` default.
- [X] T024 [P] Write a failing unit test
      `patient-service/src/test/java/com/dentalclinic/patient/record/PeselValidatorTest.java`:
      valid checksum accepted, invalid format/checksum rejected, `null` accepted (research.md #1)
- [X] T025 Implement `patient-service/src/main/java/com/dentalclinic/patient/record/PeselValidator.java` —
      makes T024 pass (6/6)
- [X] T026 [P] Write a failing Testcontainers test
      `patient-service/src/test/java/com/dentalclinic/patient/audit/PatientAuditWriterTest.java`:
      `append()` from `patient-service` correctly continues an existing, pre-seeded
      (auth-service-style) hash chain in the shared `audit_log_entry` table, and always leaves
      `target_account_id` null / `target_patient_record_id` populated. Table structure (owned by
      auth-service) is recreated locally in the test, mirroring V5/V12, minus the `staff_account`
      FK (out of scope for this test).
- [X] T027 Implement `patient-service/src/main/java/com/dentalclinic/patient/audit/{PatientAuditEventType.java,PatientAuditEntryHash.java,PatientAuditWriter.java}`
      (same `pg_advisory_xact_lock` pattern and **identical fixed lock key** as T013's
      `AuditLogWriter`, plain `JdbcTemplate` — no JPA entity for `audit_log_entry` since this
      service doesn't own that table's migration history — writes to the shared table, populates
      `target_patient_record_id`) — makes T026 pass (depends on T011, T013).

**Checkpoint**: Foundation ready — `auth-service` knows about `ASSISTANT`, the audit trail is
multi-writer-safe, `patient-service` can authenticate requests and persist a `PatientRecord`, and
the frontend has a shell to host feature screens in. User story implementation can now begin.

---

## Phase 3: User Story 1 - Założenie kartoteki nowego pacjenta (Priority: P1) 🎯 MVP

**Goal**: Rejestrator/lekarz can create, search, and edit a patient's basic data.

**Independent Test**: spec.md US1 Acceptance Scenarios 1–6 (create with/without PESEL, reject bad
checksum, reject duplicate PESEL, deny non-RECEPTION/DOCTOR/ADMINISTRATOR roles from the form).

### Tests for User Story 1 ⚠️

- [X] T028 [P] [US1] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/PatientCreateApiTest.java`:
      `POST /patients` — `201` with and without PESEL, `400` on bad checksum, `409` on duplicate
      PESEL, `404` for an `ASSISTANT` caller, `401` unauthenticated (6/6)
- [X] T029 [P] [US1] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/PatientSearchApiTest.java`:
      `GET /patients?q=` — matches by last-name fragment and by exact PESEL, `404` for
      `ADMINISTRATOR`; a successful search writes one `PATIENT_RECORD_VIEWED` audit entry for the
      call (FR-007/SC-003) (4/4)
- [X] T030 [P] [US1] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/PatientDetailApiTest.java`:
      `GET /patients/{id}` readable by `RECEPTION`/`DOCTOR`/`ASSISTANT`, and writes a
      `PATIENT_RECORD_VIEWED` audit entry (FR-007/SC-003); `PATCH /patients/{id}`
      only by `RECEPTION`/`DOCTOR`, `404` otherwise, `400`/`409` mirrored from create (7/7)
- [X] T031 [P] [US1] Write a failing Vitest test
      `frontend/src/app/features/patients/patient-create/patient-create.component.spec.ts`
      (5/5 — includes client-side PESEL-checksum rejection and 409-duplicate error mapping)
- [X] T032 [P] [US1] Write a failing Vitest test
      `frontend/src/app/features/patients/patient-search/patient-search.component.spec.ts` (3/3)
- [X] T033 [P] [US1] Write a failing Playwright e2e test `frontend/e2e/us1-patient-create.spec.ts`
      covering spec.md US1 Acceptance Scenarios 1–6 (Scenario 6's ASSISTANT half is deferred to
      T061, Phase 6, which adds the seed account; its server-side denial is already proven by
      `PatientCreateApiTest#assistant_isDenied404`). Also fixed `frontend/e2e/us1-login-rbac.spec.ts`
      (001), whose Scenarios 1/2/SC-001/5/6/7 asserted the old `/reception`/`/doctor` placeholder
      routes T016/T040 replaced with the shared `/patients` screen. Verified: `npx playwright test
      --list` parses both files (56 tests across projects); full execution needs the live
      docker-compose stack, same as the rest of this suite (frontend-e2e CI job still `if: false`).
- [X] Bonus (not separately ticketed, needed for FR-011 to be reachable at all and for T050/T056's
      "wire into the patient-detail view" instructions to make sense):
      `frontend/src/app/features/patients/patient-detail/{patient-detail.component.ts,patient-detail.component.spec.ts}`
      — basic-data view/edit (2/2 tests) plus the empty tab shells US2/US3 fill in.

### Implementation for User Story 1

- [X] T034 [US1] Implement `patient-service/src/main/java/com/dentalclinic/patient/record/PatientCreateService.java`
      (PESEL validation via T025, duplicate check via the T017 partial unique index + a
      `DataIntegrityViolationException` catch as the race-condition backstop, audit via T027)
      (depends on T023, T025, T027)
- [X] T035 [US1] Add search query methods to `PatientRecordRepository` (case-insensitive
      last-name match via `findByLastNameIgnoreCaseContaining`, exact-PESEL match) and
      `PatientSearchService` (also handles `GET /patients/{id}`'s read + audit)
- [X] T036 [US1] Implement `patient-service/src/main/java/com/dentalclinic/patient/record/PatientUpdateService.java`
      (edit basic data + before/after audit; duplicate-PESEL check excludes the record's own id)
- [X] T037 [US1] Implement `patient-service/src/main/java/com/dentalclinic/patient/api/PatientController.java`
      + `GlobalExceptionHandler`/`PatientCreateRequest`/`PatientSummaryResponse`/`PatientDetailResponse`:
      `POST /patients`, `GET /patients`, `GET /patients/{id}`, `PATCH /patients/{id}`, with
      `@PreAuthorize` per `rbac-policy.md` — makes T028–T030 pass (depends on T034–T036).
      `GET /patients` and `GET /patients/{id}` additionally write a `PATIENT_RECORD_VIEWED` audit
      entry via `PatientAuditWriter` (T027) — one entry per search call (with query/hit-count in
      `metadata`) or per detail read (FR-007/SC-003, data-model.md). Audit before/after snapshots
      use Jackson's `ObjectMapper` (Jackson 3 / `tools.jackson.*` packages under Spring Boot 4.1,
      not the legacy `com.fasterxml.jackson.databind` — its `writeValueAsString` is unchecked now)
      rather than hand-built JSON strings, since patient names/addresses are free text where
      string concatenation risked invalid JSON.
- [X] T038 [P] [US1] Implement `frontend/src/app/features/patients/patient-search/patient-search.component.ts` —
      makes T032 pass
- [X] T039 [P] [US1] Implement `frontend/src/app/features/patients/patient-create/patient-create.component.ts`
      (mirrors the PESEL checksum check client-side for UX only, via new `pesel-validator.ts`) —
      makes T031 pass
- [X] T040 [US1] Wire the patient-search/patient-create/patient-detail routes into
      `app.routes.ts` under the existing `AppShellComponent` group (depends on T015, T016, T038,
      T039) — makes T033 pass. Retired the placeholder `/reception`/`/doctor`/`/assistant` paths
      (`roleHomeRoute()` now points RECEPTION/DOCTOR/ASSISTANT at the shared `/patients` screen).
      Verified: `npm run lint`, `npm test` (34/34), `npm run build` all pass.

**Checkpoint**: User Story 1 is fully functional and independently testable — deployable as the MVP.

---

## Phase 4: User Story 2 - Wizualne oznaczanie stanu uzębienia (Priority: P2)

**Goal**: Doctor/assistant can view and edit a patient's tooth chart.

**Independent Test**: spec.md US2 Acceptance Scenarios 1–4 (toggle a tooth, revert it, new record
defaults all-healthy, `RECEPTION` denied).

### Tests for User Story 2 ⚠️

- [X] T041 [P] [US2] Write a failing test
      `patient-service/src/test/java/com/dentalclinic/patient/toothchart/ToothStateAutoCreationTest.java`:
      creating a patient yields exactly 32 `HEALTHY` rows, one per valid FDI tooth number
- [X] T042 [P] [US2] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/ToothChartApiTest.java`:
      `GET`/`PATCH /patients/{id}/tooth-chart[/{toothNumber}]` — `200` for `DOCTOR`/`ASSISTANT`,
      `404` for `RECEPTION`; `GET` writes a `TOOTH_CHART_VIEWED` audit entry (FR-007/SC-003);
      `PATCH` writes a `TOOTH_STATE_CHANGED` audit entry with before/after
- [X] T043 [P] [US2] Write a failing Vitest test
      `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.spec.ts`: jaw SVG
      renders 32 teeth, tapping one selects it, toggling updates its visual state
- [X] T044 [P] [US2] Write a failing Playwright e2e test `frontend/e2e/us2-tooth-chart.spec.ts`
      covering spec.md US2 Acceptance Scenarios 1–4. Scenario 4 (RECEPTION denied) is proven
      client-side (no "Stan uzębienia" tab renders — `PatientDetailComponent.canViewToothChart`,
      added alongside T050) with the server-side 404 already covered by
      `ToothChartApiTest#reception_isDenied404_on*`; the ASSISTANT half of Scenarios 1–3 is
      deferred to T061 like T033's own ASSISTANT gap, already proven server-side by
      `ToothChartApiTest#assistant_canToggleToothStatus`. Verified: `npx playwright test --list`
      parses (92 tests across projects); full execution needs the live docker-compose stack (T063).

### Implementation for User Story 2

- [X] T045 [US2] Implement
      `patient-service/src/main/java/com/dentalclinic/patient/toothchart/{ToothState.java,ToothStateRepository.java}`
      (depends on T018)
- [X] T046 [US2] Implement `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothChartInitializer.java`:
      creates the 32 `ToothState` rows on patient creation, invoked from `PatientCreateService`
      (T034) — makes T041 pass
- [X] T047 [US2] Implement `patient-service/src/main/java/com/dentalclinic/patient/toothchart/ToothChartService.java`:
      read full chart, update a single tooth's status + audit via `PatientAuditWriter` (T027)
- [X] T048 [US2] Implement `patient-service/src/main/java/com/dentalclinic/patient/api/ToothChartController.java`:
      `GET`/`PATCH` endpoints, `@PreAuthorize` restricted to `DOCTOR`/`ASSISTANT` — makes T042 pass
      (depends on T047). `GET` additionally writes a `TOOTH_CHART_VIEWED` audit entry via
      `PatientAuditWriter` (T027) (FR-007/SC-003, data-model.md)
- [X] T049 [P] [US2] Implement `frontend/src/app/features/patients/tooth-chart/tooth-chart.component.ts`:
      hand-built inline SVG adult jaw (32 teeth, FDI layout), tap-to-select + healthy/sick toggle —
      makes T043 pass
- [X] T050 [US2] Wire the tooth-chart tab into the patient-detail view/routing (depends on T016,
      T049) — makes T044 pass

**Checkpoint**: User Stories 1 AND 2 both work independently.

---

## Phase 5: User Story 3 - Podgląd historii wizyt pacjenta z poziomu kartoteki (Priority: P3)

**Goal**: Rejestrator/lekarz see a read-only visit-history placeholder.

**Independent Test**: spec.md US3 Acceptance Scenarios 1–2 (empty state shown, no add-entry
affordance exists).

### Tests for User Story 3 ⚠️

- [X] T051 [P] [US3] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/VisitHistoryApiTest.java`:
      `GET /patients/{id}/visit-history` — `200` empty array for `RECEPTION`/`DOCTOR`, `404` for
      `ASSISTANT`/`ADMINISTRATOR`
- [X] T052 [P] [US3] Write a failing Vitest test
      `frontend/src/app/features/patients/visit-history/visit-history.component.spec.ts`: renders
      an empty-state message; no add-entry control is present anywhere in the DOM
- [X] T053 [P] [US3] Write a failing Playwright e2e test
      `frontend/e2e/us3-visit-history-placeholder.spec.ts` covering spec.md US3 Acceptance
      Scenarios 1–2. Verified: `npx playwright test --list` parses (100 tests across projects);
      full execution needs the live docker-compose stack (T063)

### Implementation for User Story 3

- [X] T054 [US3] Implement `patient-service/src/main/java/com/dentalclinic/patient/visithistory/VisitHistoryController.java`:
      `GET` endpoint always returning `[]`, `@PreAuthorize` restricted to `RECEPTION`/`DOCTOR` —
      makes T051 pass
- [X] T055 [P] [US3] Implement `frontend/src/app/features/patients/visit-history/visit-history.component.ts`
      (empty-state only, no write UI anywhere) — makes T052 pass
- [X] T056 [US3] Wire the visit-history tab into the patient-detail view/routing (depends on T016,
      T055) — makes T053 pass

**Checkpoint**: All three user stories are independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: RODO export/erasure (FR-009/FR-010 — not tied to a specific spec.md priority tier)
and end-to-end hardening.

- [X] T057 [P] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/PatientExportApiTest.java`:
      `POST /patients/{id}/export` — `200` for `DOCTOR`, `404` for `ADMINISTRATOR`/`RECEPTION`/
      `ASSISTANT` (research.md #6 — administrator deliberately excluded from clinical-data export)
- [X] T058 [P] Write a failing contract test
      `patient-service/src/test/java/com/dentalclinic/patient/api/PatientErasureApiTest.java`:
      `POST /patients/{id}/erasure-request` — `202` for `DOCTOR`, `404` otherwise; a
      `PATIENT_DATA_ERASURE_REQUESTED` audit entry is recorded
- [X] T059 Implement `patient-service/src/main/java/com/dentalclinic/patient/record/PatientExportService.java`
      + controller endpoint (`@PreAuthorize` `DOCTOR` only) — makes T057 pass
- [X] T060 Implement `patient-service/src/main/java/com/dentalclinic/patient/record/PatientErasureService.java`
      + controller endpoint (`@PreAuthorize` `DOCTOR` only), records
      `PATIENT_DATA_ERASURE_REQUESTED` — makes T058 pass. The actual anonymization/retention job
      that later emits `PATIENT_DATA_ERASURE_COMPLETED` is out of this feature's tested scope
      (spec.md does not specify the retention-period mechanics) — leave a `TODO` referencing this
      task for that follow-up. This deferral is a reviewed, constitution-compliant exception,
      documented in plan.md's Constitution Check (Principle II row) — not a silent gap
- [X] T061 [P] Add a fourth seeded test account (`assistant@clinic.test` / `ASSISTANT`, MFA
      pre-enrolled) to `backend/src/main/java/com/dentalclinic/auth/e2eseed/`, per quickstart.md
      Prerequisites
- [X] T062 [P] Extend `docker-compose.yml`'s e2e-seed wiring so `patient-service` picks up the
      same seeded accounts/session store as `auth-service` (shared Postgres — no new seed
      mechanism, just confirm the volume/env wiring covers both services). The seed-file/volume
      wiring itself needed no change: `patient-service` already points `DB_URL` at the same
      `dental_clinic_auth` database `auth-service` provisions (T004/docker-compose.yml), so it
      reads the same `spring_session`/`spring_session_attributes`/`staff_account`-derived session
      data `E2eSeedRunner` populates via `auth-service` — no separate seed file or volume is
      needed for `patient-service` itself (it never writes `seed-accounts.json`, only
      auth-service does). **T063 live validation did find a real ordering bug** though: `depends_on:
      auth-service: condition: service_started` only waits for the container to start, not for
      its Flyway migrations to finish — on a genuinely fresh database, `patient-service`'s own
      Flyway could win the race and migrate the empty `public` schema first, leaving
      `auth-service`'s Flyway unable to start (finds a non-empty schema with no
      `flyway_schema_history` table). Fixed by adding a TCP-based healthcheck to `auth-service`
      (port 8080 only accepts connections after its full Spring context — including
      Flyway — has finished) and changing `patient-service`'s dependency to
      `condition: service_healthy`.
- [X] T063 Run the full `quickstart.md` validation end-to-end against `docker-compose` (all
      scenarios, including Scenario 0 cross-service session sharing and Scenario 6 concurrent-
      writer audit-chain integrity), and manually verify SC-001–SC-005 against the running stack.
      **This was the first time the feature actually ran against the live stack** (prior
      Playwright verification was `--list`-only) and surfaced five real, previously-undetected
      defects, all now fixed with regression tests/coverage:
      1. `frontend/nginx.conf` and `proxy.conf.js` (was `proxy.conf.json`) had no `/patients`
         routing at all — patient-service was completely unreachable from the frontend. Fixed by
         adding routing, then discovered `/patients` is *also* an Angular client-side route
         (search/create/detail) at the same paths as the API — resolved via an `Accept`-header
         heuristic (browsers send `text/html` on navigation, Angular's HttpClient doesn't).
      2. Two independent Flyway histories sharing one `public` schema meant whichever service
         migrated first left the other unable to start ("non-empty schema, no history table").
         Fixed with `baseline-on-migrate: true` + `baseline-version: 0` on patient-service's
         Flyway config, plus a TCP healthcheck on `auth-service` so `patient-service` reliably
         waits for it (`docker-compose.yml`, T062).
      3. `patient-service`'s `SecurityConfig` was missing the `CsrfCookieFilter` equivalent
         `auth-service` already needed for the identical reason (001's own
         `CsrfCookieIntegrationTest` precedent) — without it the `XSRF-TOKEN` cookie was never
         written, so every mutating request from a real browser failed. New
         `com.dentalclinic.patient.session.CsrfCookieFilter` + regression test.
      4. Deeper CSRF bug: `SessionAuthenticationFilter` re-authenticates fresh from JDBC on every
         request (by design, stateless), which made Spring Security's default
         `CsrfAuthenticationStrategy` treat every request as a new login and delete the CSRF
         cookie it had just issued — breaking every request after the first. Fixed via
         `.csrf(csrf -> csrf.sessionAuthenticationStrategy(new NullAuthenticatedSessionStrategy()))`
         (no session-fixation risk exists here to protect against — this service never creates an
         `HttpSession`) + a regression test driving two sequential requests through a real seeded
         session (not the `SecurityMockMvcRequestPostProcessors.user()` shortcut, which doesn't
         trigger this).
      5. `patient-search.component.ts`'s `<form (ngSubmit)="search()">` never actually bound —
         `(ngSubmit)` is an `NgForm`/`FormGroupDirective` output, and only `ReactiveFormsModule`
         (no `[formGroup]`, no `FormsModule`) was imported — so clicking "Szukaj" fell through to
         an un-prevented native browser form submission (`GET /patients?`, no query), and search
         was completely broken end-to-end despite `component.search()` unit tests passing. Fixed
         with a native `(submit)` handler calling `event.preventDefault()`; new Vitest test drives
         a real DOM submit event (confirmed red on the old binding, green on the fix).
      Also found and fixed a related HTTP-caching bug the `/patients` Accept-header fix
      introduced: nginx sent no `Vary: Accept`, so a browser could reuse a cached `index.html`
      navigation response for a same-URL XHR with a different `Accept` header (surfaced only by a
      direct deep-link immediately followed by the component's own data fetch); fixed with
      `add_header Vary Accept always;`.
      Also added `GET /auth/session` (`auth-service`, `LoginController`/`SessionInfoResponse` +
      integration test) and frontend rehydration (`AuthService.rehydrateSession()` +
      `provideAppInitializer`, `auth.service.spec.ts`): `AuthState.currentRole` was previously
      populated only immediately after login and never restored, so any full-page reload or deep
      link on an already-authenticated session incorrectly bounced the user to `/login` — this is
      a cross-cutting fix to already-shipped 001 code, flagged per CLAUDE.md governance guidance
      and confirmed with the user before implementing.
      **Final verification**: `./gradlew build` green on both `backend` and `patient-service`
      (full suites, checkstyle, spotless); a clean `docker compose down -v && up -d` boots both
      services successfully without manual intervention; a serial, single-worker Playwright run
      of all three 002 spec files (`us1-patient-create.spec.ts`, `us2-tooth-chart.spec.ts`,
      `us3-visit-history-placeholder.spec.ts`) against a freshly reset database passes 16/16 —
      covering Scenarios 0–4 and SC-001/SC-002/SC-004. Scenario 5 (RODO export/erasure) and
      Scenario 6 (concurrent-writer audit-chain integrity) were verified via the existing backend
      contract/integration tests (`PatientExportApiTest`, `PatientErasureApiTest`,
      `PatientAuditWriterTest`) rather than re-driven manually through the UI, since no frontend
      screens exist for either (out of this feature's scope — export/erasure are API-only,
      quickstart.md Scenario 5; audit-chain concurrency was already proven at the JDBC level).
      A full multi-project (mobile/tablet/desktop × 2 workers) Playwright run was also attempted;
      it surfaced no new defects beyond what's listed above, but is not a clean signal on its own
      — concurrent workers against one shared Postgres volume produce data collisions (duplicate
      PESELs/names) and two pre-existing, out-of-scope staleness issues in 001's own test files
      (`us2-audit-log.spec.ts`'s stale `**/reception` route reference, retired by T040; an
      apparently-flaky email-capture timing assertion in `us3-account-management.spec.ts`) —
      consistent with why `frontend-e2e` CI is already `if: false`, not a regression from this
      feature.
- [X] T063a [P] Add Playwright timing assertions for SC-001/SC-002/SC-004 (spec.md — currently
      only manually verified once by T063, no automated regression guard exists): patient creation
      completes within 2 minutes in `frontend/e2e/us1-patient-create.spec.ts` (SC-001), a tooth-state
      toggle is reflected within 15 seconds of opening the record in
      `frontend/e2e/us2-tooth-chart.spec.ts` (SC-002), and a patient search returns within 10
      seconds in `frontend/e2e/us1-patient-create.spec.ts` or a dedicated search spec (SC-004) —
      mirrors 001's T086 precedent for its own SC-001 latency assertion. Verified: `npx playwright
      test --list` parses (120 tests across projects); full execution needs the live
      docker-compose stack (T063).
- [ ] T064 Document the security/compliance self-review in the PR description (Development
      Workflow gate — this change touches patient data, authz, and audit logging; per
      constitution.md's solo-contributor risk-tiering, self-attested review is sufficient but MUST
      be documented, and auto-merge MUST NOT be enabled for this PR)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately.
- **Foundational (Phase 2)**: Depends on Setup (needs `patient-service/` to exist to add code to
  it) — BLOCKS all user stories. Note the internal ordering constraint: T006 before T007; T009
  before T011 and before/alongside T017; T011/T018 before T020; T013/T011 before T027.
- **User Stories (Phase 3–5)**: All depend on Foundational completion. Independently
  implementable/testable afterward, though a sequential P1→P2→P3 delivery order is recommended
  (US2/US3 both extend the patient-detail view US1 introduces).
- **Polish (Phase 6)**: Depends on all three user stories being complete (export/erasure return
  the tooth chart and, nominally, visit history).

### User Story Dependencies

- **US1 (P1)**: No dependency on US2/US3.
- **US2 (P2)**: Independently testable via its own contract/e2e tests, but its UI is a tab on the
  patient-detail screen US1's routing (T040) establishes — implement after US1 for a smoother
  integration, though its backend (T045–T048) has no code dependency on US1's implementation
  tasks beyond the shared `PatientCreateService` hook point (T046).
- **US3 (P3)**: Same relationship as US2 — independent contract, shares the patient-detail shell.

### Parallel Opportunities

- All Setup tasks (T002–T005) can run in parallel once T001 exists.
- Within Foundational: the `auth-service` migration track (T006–T011), the frontend-shell track
  (T014–T016), and the `patient-service` schema/session/entity track (T017–T027) are largely
  parallel across tracks, though each track is internally sequential where noted above.
- All `[P]`-marked test-writing tasks within a user-story phase can run in parallel with each
  other (different files).
- US2 and US3 can be implemented in parallel by different developers once Foundational is done,
  provided they coordinate on the shared patient-detail routing file (T016).

---

## Parallel Example: User Story 1

```bash
# Launch all US1 tests together (different files, no shared state):
Task: "Contract test PatientCreateApiTest.java"
Task: "Contract test PatientSearchApiTest.java"
Task: "Contract test PatientDetailApiTest.java"
Task: "Vitest test patient-create.component.spec.ts"
Task: "Vitest test patient-search.component.spec.ts"
Task: "Playwright test us1-patient-create.spec.ts"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 (Setup) and Phase 2 (Foundational) — the latter is the larger lift here,
   since it includes the cross-service RBAC/audit/session-sharing plumbing.
2. Complete Phase 3 (US1).
3. **STOP and VALIDATE**: run quickstart.md Scenarios 0, 1, 4 (session sharing, patient creation,
   search) independently.
4. Deploy/demo if ready — a clinic can already register patients at this point.

### Incremental Delivery

1. Setup + Foundational → two-service foundation ready (session sharing + audit trail proven).
2. US1 → test independently → deploy/demo (MVP).
3. US2 → test independently → deploy/demo (tooth chart added).
4. US3 → test independently → deploy/demo (visit-history placeholder added).
5. Polish (RODO export/erasure + hardening) → final compliance close-out for this feature.

---

## Notes

- FR-008 (encryption at rest/in transit) requires no new task — it's satisfied by the existing
  RDS/Aurora KMS storage encryption and ALB TLS termination 001 already provisions (plan.md
  Constraints); verify this holds during T063's quickstart validation rather than building
  anything new for it.
- `[P]` tasks touch different files with no unresolved dependency; sequential tasks in the same
  package/file are intentionally left unmarked.
- Commit after each task or logical group, per constitution.md's phase-end "full test suite
  passing + commit" rule — treat each Phase 3–6 boundary above as one such phase-end checkpoint.
- Verify every test actually fails before writing its implementation (Red before Green).

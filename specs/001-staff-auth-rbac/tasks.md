---

description: "Task list template for feature implementation"
---

# Tasks: Rejestracja i logowanie personelu z kontrolą dostępu opartą na rolach (RBAC)

**Input**: Design documents from `/specs/001-staff-auth-rbac/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md (all present)

**Tests**: Included for every user story per constitution Principle I (Test-First Development,
NON-NEGOTIABLE) — write and confirm failing before the corresponding implementation task.

**Organization**: Tasks are grouped by user story (US1/US2/US3 from spec.md) to enable independent
implementation and testing of each story, per plan.md's Project Structure (backend organized by
domain: account, role, session, mfa, passwordreset, auditlog).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (US1, US2, US3)
- File paths are exact, relative to repo root

## Path Conventions

Web app per plan.md Structure Decision: `backend/src/main/java/com/dentalclinic/auth/...`,
`backend/src/test/java/com/dentalclinic/auth/...`, `frontend/src/app/...`, `frontend/e2e/...`,
plus `infra/terraform/`, `helm/`, `.github/workflows/` (this is the repo's first feature, so it
also creates this baseline layout).

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Repository/project initialization — nothing below exists yet.

- [X] T001 Create top-level directories `backend/`, `frontend/`, `infra/terraform/`,
      `helm/auth-service/`, `helm/frontend/`, `.github/workflows/` per plan.md Project Structure
- [X] T002 Initialize backend Gradle project (Kotlin DSL, committed `./gradlew` wrapper — no
      local Gradle/Maven install required) in `backend/` targeting Java 25 via toolchain
      auto-provisioning (Foojay resolver), with the Spring Boot 4.1.x Gradle plugin and Spring
      Web, Spring Security, Spring Data JPA, Flyway, Spring Session JDBC starters (research.md
      #1, #2, #5, #12)
- [X] T003 [P] Initialize frontend Angular 21 project in `frontend/` with standalone components,
      Angular Material, and Vitest as the configured unit test runner (research.md #3, #9)
- [X] T004 [P] Add `java-totp` dependency to `backend/build.gradle.kts` for TOTP MFA (research.md #4)
- [X] T005 [P] Configure backend linting/formatting (Checkstyle + Spotless Gradle plugins) in
      `backend/build.gradle.kts` / `backend/checkstyle.xml`
- [X] T006 [P] Configure frontend linting/formatting (ESLint + Prettier) in
      `frontend/eslint.config.js` / `frontend/.prettierrc`
- [X] T007 [P] Add JUnit 5, Mockito, and Testcontainers (PostgreSQL module) test dependencies to
      `backend/build.gradle.kts` (research.md #9)
- [X] T008 [P] Add Playwright to `frontend/` with a mobile-viewport project profile in
      `frontend/playwright.config.ts` (research.md #9, Principle IV)
- [X] T009 Create Terraform baseline in `infra/terraform/` — provider config, remote state
      backend, separate DEV/PROD workspaces, VPC + EKS + RDS/Aurora PostgreSQL modules
      (Technology Stack Constraints; Environments & Release Process)
- [X] T009a [P] Create Terraform `aws_kms_key` (+ alias) in `infra/terraform/` for encrypting
      the MFA TOTP secret column (FR-013, data-model.md `MfaEnrollment.totp_secret_encrypted`;
      research.md #11) (depends on T009)
- [X] T010 [P] Create Helm chart skeleton `helm/auth-service/Chart.yaml` — its own release,
      separate from any future lower-tier module (plan.md Risk Tier section, Principle V)
- [X] T011 [P] Create Helm chart skeleton `helm/frontend/Chart.yaml` for the Angular static build
- [X] T012 [P] Create GitHub Actions CI workflow `.github/workflows/ci.yml` — build + run backend
      (JUnit5/Testcontainers) and frontend (Vitest + Playwright) test suites on every PR
      (Principle VI; Development Workflow gate — CI must pass before merge)
- [X] T013 [P] Create GitHub Actions Terraform plan/apply workflow
      `.github/workflows/terraform.yml` (Principle VI — no manual `terraform apply`)
- [X] T014 [P] Create GitHub Actions Helm/Argo CD sync workflow `.github/workflows/deploy.yml`,
      triggered on merge to main, driving canary stages via Argo Rollouts (Principle VI;
      Environments & Release Process)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, security baseline, and shared services every user story depends on.

**⚠️ CRITICAL**: No user story work may begin until this phase is complete.

- [X] T015 Flyway migration `backend/src/main/resources/db/migration/V1__staff_account.sql` —
      StaffAccount table (data-model.md)
- [X] T016 Flyway migration `.../V2__mfa_enrollment.sql` — MfaEnrollment table
- [X] T017 Flyway migration `.../V3__session.sql` — Spring Session JDBC schema
- [X] T018 Flyway migration `.../V4__password_reset_token.sql` — PasswordResetToken table
- [X] T019 Flyway migration `.../V5__audit_log.sql` — AuditLogEntry table with hash-chain columns
      (`previous_entry_hash`, `entry_hash`); `REVOKE UPDATE, DELETE` from the application's DB
      role in the same migration so tamper-evidence is enforced from day one (research.md #7)
- [X] T020 [P] StaffAccount JPA entity + repository in
      `backend/src/main/java/com/dentalclinic/auth/account/StaffAccount.java` and
      `StaffAccountRepository.java` (depends on T015)
- [X] T021 [P] Role enum in `backend/src/main/java/com/dentalclinic/auth/role/Role.java`
- [X] T022 [P] MfaEnrollment JPA entity + repository in
      `backend/src/main/java/com/dentalclinic/auth/mfa/MfaEnrollment.java` (depends on T016)
- [X] T022a IAM (IRSA) permissions for the backend pod to call `kms:Encrypt`/`kms:Decrypt` on
      the T009a key, plus a JPA `AttributeConverter` that encrypts/decrypts
      `MfaEnrollment.totp_secret_encrypted` via KMS (FR-013) in
      `backend/src/main/java/com/dentalclinic/auth/mfa/TotpSecretConverter.java`
      (depends on T009a, T022)
- [X] T023 [P] PasswordResetToken JPA entity + repository in
      `backend/src/main/java/com/dentalclinic/auth/passwordreset/PasswordResetToken.java`
      (depends on T018)
- [X] T024 [P] AuditLogEntry JPA entity + repository in
      `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditLogEntry.java` (depends on T019)
- [X] T025 AuditLogWriter service (computes and appends the hash chain per data-model.md) in
      `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditLogWriter.java` (depends on T024)
- [X] T026 Spring Security base config (Argon2id `PasswordEncoder`, CORS/CSRF policy, unauthenticated
      → 401) in `backend/src/main/java/com/dentalclinic/auth/config/SecurityConfig.java`
      (research.md #6)
- [X] T027 Spring Session JDBC config — shared store, 15-minute sliding idle timeout and an
      8-hour hard cap independent of activity (spec Assumptions) in
      `backend/src/main/java/com/dentalclinic/auth/config/SessionConfig.java`
      (research.md #5, depends on T017)
- [X] T028 Global exception handler mapping RBAC scope denials to `404` (never `403`) in
      `backend/src/main/java/com/dentalclinic/auth/api/GlobalExceptionHandler.java`
      (research.md #8; contracts/rbac-policy.md rule 2)
- [X] T029 [P] Angular app shell — standalone root component, routing, base HTTP client config in
      `frontend/src/app/app.config.ts` and `frontend/src/app/app.routes.ts`
- [X] T030 [P] Angular auth HTTP interceptor (attaches session cookie, redirects to login on 401)
      in `frontend/src/app/core/auth/auth.interceptor.ts`
- [X] T031 [P] Angular role-based route guards — **UX redirect only, not the authorization source
      of truth** (server enforces via T028) — in `frontend/src/app/core/auth/role.guard.ts`

**Checkpoint**: Foundation ready — user story implementation can begin.

---

## Phase 3: User Story 1 - Logowanie personelu z dostępem zgodnym z rolą (Priority: P1) 🎯 MVP

**Goal**: Staff can log in with email/password + mandatory TOTP MFA and see only their
role's functions/data; self-service password reset works; brute-force lockout and
deactivated-account denial are enforced.

**Independent Test**: Log in as each of three seeded test accounts (recepcja/lekarz/administrator)
and verify each sees only its role's functions/data, out-of-role direct access is denied, and
password reset completes end-to-end (spec.md "Independent Test", User Story 1).

### Tests for User Story 1 ⚠️ Write first — confirm they FAIL before implementation

- [X] T032 [P] [US1] Contract test `POST /auth/login` (valid, wrong password, locked, deactivated)
      in `backend/src/test/java/com/dentalclinic/auth/api/LoginControllerContractTest.java`
- [X] T033 [P] [US1] Contract test `POST /auth/mfa/verify` (valid/invalid/expired pre-auth token)
      in `backend/src/test/java/com/dentalclinic/auth/api/MfaControllerContractTest.java`
- [X] T034 [P] [US1] Contract test `POST /auth/password-reset/request` and `/confirm` in
      `backend/src/test/java/com/dentalclinic/auth/api/PasswordResetControllerContractTest.java`
- [X] T035 [P] [US1] Integration test (Testcontainers): 5 consecutive failed attempts → 15-minute
      lockout (FR-011) in
      `backend/src/test/java/com/dentalclinic/auth/account/AccountLockoutIntegrationTest.java`
- [X] T036 [P] [US1] Integration test (Testcontainers): out-of-role direct request returns `404`,
      not `403` (FR-005) in
      `backend/src/test/java/com/dentalclinic/auth/role/RbacEnforcementIntegrationTest.java`
- [X] T037 [P] [US1] Integration test (Testcontainers): deactivated account cannot log in (FR-010)
      in `backend/src/test/java/com/dentalclinic/auth/account/DeactivatedAccountLoginTest.java`
- [X] T038 [P] [US1] Vitest unit tests for login form + MFA challenge components in
      `frontend/src/app/features/auth/login/login.component.spec.ts`
- [X] T039 [P] [US1] Playwright e2e test covering spec.md US1 Acceptance Scenarios 1–7 in
      `frontend/e2e/us1-login-rbac.spec.ts`
- [X] T039a [P] [US1] Integration test (Testcontainers): session expires after 15 minutes of
      inactivity — subsequent request with the expired session ID returns `401` (FR-012) in
      `backend/src/test/java/com/dentalclinic/auth/session/SessionIdleTimeoutIntegrationTest.java`
- [X] T039b [P] [US1] Integration test (Testcontainers): session is invalidated at the 8-hour
      hard cap even when kept continuously active (FR-012, spec Assumptions) in
      `backend/src/test/java/com/dentalclinic/auth/session/SessionHardCapIntegrationTest.java`
- [X] T039c [P] [US1] Integration test (Testcontainers): password creation/reset rejects passwords
      shorter than 12 characters and passwords found on the breached-password list (FR-002a) in
      `backend/src/test/java/com/dentalclinic/auth/account/PasswordPolicyValidatorTest.java`
- [X] T039d [P] [US1] Integration test (Testcontainers): repeated invalid MFA codes increment the
      same `failed_login_count` used for password failures and trigger lockout at the same
      5-attempt/15-minute threshold (FR-011a) in
      `backend/src/test/java/com/dentalclinic/auth/account/MfaFailureLockoutIntegrationTest.java`
- [X] T039e [P] [US1] Integration test (Testcontainers): login attempts from a single source IP
      against multiple different accounts are rejected with `429` once the per-IP threshold is
      exceeded, independent of any single account's own lockout state (FR-011b) in
      `backend/src/test/java/com/dentalclinic/auth/account/IpRateLimitIntegrationTest.java`
- [X] T039f [P] [US1] Integration test (Testcontainers): account lockout (FR-011) triggers an
      email notification to the account's registered address (FR-011c) in
      `backend/src/test/java/com/dentalclinic/auth/account/LockoutNotificationIntegrationTest.java`
- [X] T039g [P] [US1] Integration test (Testcontainers): pre-auth token issued by `POST
      /auth/login` is rejected by `POST /auth/mfa/verify` exactly 5 minutes after issuance
      (FR-015a) in
      `backend/src/test/java/com/dentalclinic/auth/account/PreAuthTokenExpiryIntegrationTest.java`

### Implementation for User Story 1

- [X] T040 [US1] AuthService — verify email/password, issue pre-auth token valid for exactly 5
      minutes (FR-015a) in
      `backend/src/main/java/com/dentalclinic/auth/account/AuthService.java` (depends on T020, T026)
- [X] T041 [US1] AccountLockoutService — `failed_login_count`/`locked_until` logic (FR-011) in
      `backend/src/main/java/com/dentalclinic/auth/account/AccountLockoutService.java`
      (depends on T040)
- [X] T041a [US1] Wire MfaService (T042) to call AccountLockoutService (T041) on an invalid TOTP
      code, incrementing the same `failed_login_count`/`locked_until` used for password failures
      (FR-011a) (depends on T041, T042)
- [X] T041b [US1] IpRateLimitService — Postgres-backed `LoginAttemptByIp` sliding-window counter
      checked/incremented in `POST /auth/login` before credential verification, rejecting with
      `429` once the per-IP threshold is exceeded, independent of per-account lockout (FR-011b,
      data-model.md `LoginAttemptByIp`; deliberately no new datastore, per plan.md Constraints) in
      `backend/src/main/java/com/dentalclinic/auth/account/IpRateLimitService.java`
      (depends on T040)
- [X] T041c [US1] Wire AccountLockoutService (T041) to send an account-lockout email notification
      via the AWS SES integration established in T043 (FR-011c) (depends on T041, T043)
- [X] T042 [US1] MfaService — TOTP secret enrollment + code verification via `java-totp` (FR-015),
      encrypting/decrypting the stored secret through T022a's `TotpSecretConverter` (FR-013) in
      `backend/src/main/java/com/dentalclinic/auth/mfa/MfaService.java` (depends on T022, T022a)
- [X] T043 [US1] PasswordResetService — token issuance/consumption (30-min validity, single use,
      FR-016, uniform response regardless of whether the email matches an account) + AWS SES email
      send (FR-017 logging) in
      `backend/src/main/java/com/dentalclinic/auth/passwordreset/PasswordResetService.java`
      (depends on T023)
- [X] T043a [US1] PasswordPolicyValidator — enforce NIST 800-63B (minimum 12 characters, rejected
      if present on a known-breached-password list, no forced character-class complexity) for
      passwords set via PasswordResetService (T043) and AccountAdminService account creation
      (T074) (FR-002a) in
      `backend/src/main/java/com/dentalclinic/auth/account/PasswordPolicyValidator.java`
      (depends on T043)
- [X] T044 [US1] LoginController (`POST /auth/login`, `POST /auth/logout`) per
      contracts/auth-api.yaml in
      `backend/src/main/java/com/dentalclinic/auth/api/LoginController.java`
      (depends on T040, T041)
- [X] T045 [US1] MfaController (`POST /auth/mfa/verify`) per contracts/auth-api.yaml in
      `backend/src/main/java/com/dentalclinic/auth/api/MfaController.java` (depends on T042)
- [X] T046 [US1] PasswordResetController (`/auth/password-reset/request`, `/confirm`) in
      `backend/src/main/java/com/dentalclinic/auth/api/PasswordResetController.java`
      (depends on T043)
- [X] T047 [US1] RBAC permission-matrix enforcement (`@PreAuthorize`) on role-scoped endpoints per
      contracts/rbac-policy.md (depends on T026, T028)
- [X] T048 [US1] Wire audit logging (`LOGIN_SUCCESS`, `LOGIN_FAILURE`, `LOGIN_DENIED_LOCKED`,
      `LOGIN_DENIED_DEACTIVATED`, `LOGIN_DENIED_RATE_LIMITED`, `MFA_FAILURE`, `PASSWORD_RESET_*`)
      into T040–T043, T041b via AuditLogWriter (FR-006, FR-011, FR-011b, FR-017)
      (depends on T025, T040–T043, T041b)
- [X] T049 [P] [US1] Angular login page (email + password step) in
      `frontend/src/app/features/auth/login/login.component.ts` — mobile-first, Angular Material
      default theme (no custom branding — deferred to feature 002)
- [X] T050 [P] [US1] Angular MFA challenge screen in
      `frontend/src/app/features/auth/login/mfa-challenge.component.ts`
- [X] T051 [P] [US1] Angular password-reset request + confirm screens in
      `frontend/src/app/features/auth/password-reset/`
- [X] T052 [US1] Angular AuthService (calls `/auth/login`, `/auth/mfa/verify`, `/auth/logout`) in
      `frontend/src/app/core/auth/auth.service.ts` (depends on T049–T051)
- [X] T053 [US1] Role-appropriate home-screen redirect after successful MFA (SC-001: <10s) wired
      into `frontend/src/app/features/auth/login/login.component.ts` routing logic
      (depends on T052)

**Checkpoint**: User Story 1 fully functional and independently testable (T032–T039, T039a,
T039b all passing).

---

## Phase 4: User Story 2 - Pełny log audytowy logowań i zmian uprawnień (Priority: P2)

**Goal**: Every login attempt (success/failure) and every role/permission change is recorded in
an append-only, tamper-evident audit log reviewable by authorized staff.

**Independent Test**: Perform a series of logins and role changes on a test admin account, then
verify every event appears in the audit log with correct who/what/when/before-after, and that no
role (including administrator) can edit or delete an entry (spec.md "Independent Test",
User Story 2).

### Tests for User Story 2 ⚠️ Write first — confirm they FAIL before implementation

- [X] T054 [P] [US2] Contract test `GET /audit-log` (admin-only, `404` for non-admin caller) in
      `backend/src/test/java/com/dentalclinic/auth/api/AuditLogControllerContractTest.java`
- [X] T055 [P] [US2] Integration test (Testcontainers): login success/failure produce correct
      entries, and no entry ever contains the plaintext password (FR-006) in
      `backend/src/test/java/com/dentalclinic/auth/auditlog/AuditLogContentIntegrationTest.java`
- [X] T056 [P] [US2] Integration test (Testcontainers): admin role change produces `ROLE_CHANGED`
      with correct `before_state`/`after_state` (FR-007) in
      `backend/src/test/java/com/dentalclinic/auth/auditlog/RoleChangeAuditIntegrationTest.java`
- [X] T057 [P] [US2] Integration test (Testcontainers): direct SQL `UPDATE`/`DELETE` against the
      audit log table fails under the application's own DB role, proving the T019 grant
      restriction holds (FR-008) in
      `backend/src/test/java/com/dentalclinic/auth/auditlog/AuditLogImmutabilityIntegrationTest.java`
- [X] T058 [P] [US2] Unit test: hash-chain verifier detects a tampered/missing row (research.md #7)
      in `backend/src/test/java/com/dentalclinic/auth/auditlog/AuditHashChainTest.java`
- [X] T059 [P] [US2] Vitest unit test for the audit log review table component in
      `frontend/src/app/features/admin/audit-log/audit-log.component.spec.ts`
- [X] T060 [P] [US2] Playwright e2e test covering spec.md US2 Acceptance Scenarios 1–4 in
      `frontend/e2e/us2-audit-log.spec.ts`

### Implementation for User Story 2

- [X] T061 [US2] AuditLogQueryService (filter by date range/event type, pagination) in
      `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditLogQueryService.java`
      (depends on T024)
- [X] T062 [US2] AuditHashChainVerifier utility in
      `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditHashChainVerifier.java`
      (depends on T025)
- [X] T063 [US2] AuditLogController (`GET /audit-log`, admin-only via RBAC) in
      `backend/src/main/java/com/dentalclinic/auth/api/AuditLogController.java`
      (depends on T047, T061)
- [X] T064 [P] [US2] Angular audit log review screen (filterable, paginated table, admin-only
      route) in `frontend/src/app/features/admin/audit-log/audit-log.component.ts`
- [X] T065 [US2] Angular AuditLogService (`GET /audit-log`) in
      `frontend/src/app/features/admin/audit-log/audit-log.service.ts` (depends on T064)

**Checkpoint**: User Stories 1 AND 2 both independently functional.

---

## Phase 5: User Story 3 - Zarządzanie kontami personelu przez administratora (Priority: P3)

**Goal**: An administrator creates, deactivates, and reactivates staff accounts and assigns roles,
without gaining any default access to patient clinical data.

**Independent Test**: As admin, create an account with a role, verify it can log in with that
role's access, then deactivate it and verify it can no longer log in (spec.md "Independent Test",
User Story 3).

### Tests for User Story 3 ⚠️ Write first — confirm they FAIL before implementation

- [X] T066 [P] [US3] Contract test `POST /accounts` (admin-only create) in
      `backend/src/test/java/com/dentalclinic/auth/api/AccountControllerContractTest.java`
- [X] T067 [P] [US3] Contract test `PATCH /accounts/{id}` (role change) in the same test class as
      T066
- [X] T068 [P] [US3] Contract test `POST /accounts/{id}/deactivate` and `/reactivate` in the same
      test class as T066
- [X] T068a [P] [US3] Contract test `POST /accounts/{id}/deactivate` returns `409` when the
      target is the last active `ADMINISTRATOR` account, and the attempt is audit-logged
      (FR-009a; Edge Cases, spec.md) in the same test class as T066
- [X] T068b [P] [US3] Contract test `POST /accounts/{id}/mfa-reset` (admin-only `404` for
      non-admin caller, `200` clears enrollment, audit-logged as `MFA_RESET`) (FR-015b) in the
      same test class as T066
- [X] T068c [P] [US3] Integration test (Testcontainers): two concurrent `POST
      /accounts/{id}/deactivate` requests targeting the two remaining active `ADMINISTRATOR`
      accounts result in exactly one succeeding and one refused with `409` — no window where both
      pass validation (FR-009b) in
      `backend/src/test/java/com/dentalclinic/auth/account/ConcurrentAdminDeactivationTest.java`
- [X] T069 [P] [US3] Integration test (Testcontainers): deactivating an account immediately
      invalidates its active session (Edge Cases, spec.md) in
      `backend/src/test/java/com/dentalclinic/auth/account/DeactivateSessionInvalidationTest.java`
- [X] T069a [P] [US3] Integration test (Testcontainers): changing an account's role immediately
      invalidates its active session(s) (FR-007a; Edge Cases, spec.md) in
      `backend/src/test/java/com/dentalclinic/auth/account/RoleChangeSessionInvalidationTest.java`
- [X] T070 [P] [US3] Integration test (Testcontainers): newly created account logs in with
      role-appropriate access within the same flow (US3 AC1) in
      `backend/src/test/java/com/dentalclinic/auth/account/AccountLifecycleIntegrationTest.java`
- [X] T071 [P] [US3] Unit test: RBAC policy matrix grants `ADMINISTRATOR` zero patient-data
      permissions (US3 AC3; contracts/rbac-policy.md rule 3) in
      `backend/src/test/java/com/dentalclinic/auth/role/AdministratorNoClinicalAccessTest.java`
- [X] T072 [P] [US3] Vitest unit tests for account management components in
      `frontend/src/app/features/admin/accounts/accounts.component.spec.ts`
- [X] T073 [P] [US3] Playwright e2e test covering spec.md US3 Acceptance Scenarios 1–3 in
      `frontend/e2e/us3-account-management.spec.ts`

### Implementation for User Story 3

- [X] T074 [US3] AccountAdminService (create/deactivate/reactivate/change-role, FR-009) in
      `backend/src/main/java/com/dentalclinic/auth/account/AccountAdminService.java`
      (depends on T020)
- [X] T074a [US3] Guard in AccountAdminService.deactivate(): atomically (`SELECT ... FOR UPDATE`
      or an equivalent serializable-transaction guard on the set of active ADMINISTRATOR accounts
      — FR-009b) reject with a `409`-mapped exception if the target is the last active
      `ADMINISTRATOR` account, and write an audit-log entry for the rejected attempt (FR-009a)
      (depends on T074)
- [X] T074b [US3] AccountAdminService.resetMfa(): delete the target account's `MfaEnrollment` row,
      forcing MFA re-enrollment on its next login (FR-015b) in `AccountAdminService.java`
      (depends on T074)
- [X] T075 [US3] Wire session invalidation into deactivate (kill all active Sessions for the
      account) in `AccountAdminService.java` (depends on T027, T074)
- [X] T075a [US3] Wire session invalidation into change-role (kill all active Sessions for the
      account so the new role takes effect immediately, FR-007a) in `AccountAdminService.java`
      (depends on T027, T074)
- [X] T076 [US3] Wire audit logging (`ACCOUNT_CREATED`, `ACCOUNT_DEACTIVATED`,
      `ACCOUNT_REACTIVATED`, `ROLE_CHANGED`) into T074 via AuditLogWriter (depends on T025, T074)
- [X] T076a [US3] Wire audit logging (`MFA_RESET`, actor/target/timestamp) into T074b via
      AuditLogWriter (FR-015b) (depends on T025, T074b)
- [X] T077 [US3] AccountController (`GET/POST /accounts`, `PATCH /accounts/{id}`,
      `/deactivate`, `/reactivate`), admin-only via RBAC, per contracts/auth-api.yaml in
      `backend/src/main/java/com/dentalclinic/auth/api/AccountController.java`
      (depends on T047, T074)
- [X] T077a [US3] Add `POST /accounts/{id}/mfa-reset` to AccountController per
      contracts/auth-api.yaml (FR-015b) (depends on T047, T074b)
- [X] T078 [P] [US3] Angular admin accounts screens (list, create form, role-change,
      deactivate/reactivate actions) in `frontend/src/app/features/admin/accounts/`
- [X] T079 [US3] Angular AccountAdminService (calls `/accounts` endpoints) in
      `frontend/src/app/features/admin/accounts/account-admin.service.ts` (depends on T078)
- [X] T079a [P] [US3] Angular UI action for admin-triggered MFA reset (button + confirmation) in
      the accounts screen (FR-015b) (depends on T078)

**Checkpoint**: All three user stories independently functional.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T080 [P] Run full quickstart.md validation (Scenarios 1–3) against a locally-deployed stack
- [X] T081 Security/compliance review pass for auth, authz, and audit-logging changes — required
      before merge (Development Workflow & Quality Gates)
- [X] T082 [P] Document the high-risk module boundary / availability approach (plan.md Risk Tier
      section) in `backend/README.md` or an ops runbook (Principle V)
- [X] T083 [P] Mobile-viewport accessibility smoke check (WCAG-focused) across all new screens
      (Principle IV)
- [X] T084 Verify SC-001 through SC-007 against the running stack (spec.md Success Criteria)
- [ ] T085 [P] Terraform DEV workspace `apply`, smoke test against it, then `destroy` (DEV is
      ephemeral per Environments & Release Process)
- [X] T085a [P] AuditLogRetentionJob — scheduled job (e.g. Spring `@Scheduled`) that deletes
      AuditLogEntry rows older than 3 years, running under a privileged DB role separate from the
      application's normal `INSERT`-only role (FR-018, data-model.md AuditLogEntry Retention) in
      `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditLogRetentionJob.java`
      (depends on T019, T024)
- [ ] T085b [P] Terraform: pin the ALB listener's minimum TLS policy to
      `ELBSecurityPolicy-TLS13-1-2-2021-06` (or equivalent TLS-1.2-minimum policy) (FR-013) in
      `infra/terraform/` (depends on T009)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup — BLOCKS all user stories.
- **User Stories (Phase 3–5)**: All depend on Foundational completion; can then proceed in
  parallel if staffed, or sequentially in priority order (US1 → US2 → US3).
- **Polish (Phase 6)**: Depends on the user stories being complete that it validates.

### User Story Dependencies

- **US1 (P1)**: Depends only on Foundational. No dependency on US2/US3.
- **US2 (P2)**: Depends only on Foundational (AuditLogEntry/AuditLogWriter live there since US1
  and US3 also write to it — see T024/T025). Independently testable per its own acceptance
  scenarios without US1/US3 being "done," though in practice US1's login flow is what produces
  the first entries to review.
- **US3 (P3)**: Depends only on Foundational. No dependency on US1/US2, though its acceptance
  scenarios (e.g. "new account can log in") exercise US1's login path.

### Within Each User Story

- Tests (T032–T039, T039a–T039g, T054–T060, T066–T068c, T069, T069a, T070–T073) MUST be written
  and confirmed FAILING before their corresponding implementation tasks (Principle I, NON-NEGOTIABLE).
- Backend entities/services before controllers; controllers before frontend integration.
- Story complete (checkpoint) before moving to the next priority, if working sequentially.

### Parallel Opportunities

- All Setup tasks marked [P] (T003–T014, T009a, excluding T001/T002/T009 which others build on).
- All Foundational tasks marked [P] (T020–T024, T029–T031) once their migration dependency lands
  (T022a is not [P] — it depends on both T009a and T022).
- Once Foundational is done, US1/US2/US3 can be staffed in parallel — see dependency notes above.
- All test tasks within a story marked [P] run in parallel (different files).

---

## Parallel Example: User Story 1

```bash
# Tests for User Story 1 (write and confirm failing together):
Task: "Contract test POST /auth/login in backend/.../LoginControllerContractTest.java"
Task: "Contract test POST /auth/mfa/verify in backend/.../MfaControllerContractTest.java"
Task: "Contract test password-reset request/confirm in backend/.../PasswordResetControllerContractTest.java"
Task: "Integration test brute-force lockout in backend/.../AccountLockoutIntegrationTest.java"
Task: "Integration test RBAC 404-not-403 in backend/.../RbacEnforcementIntegrationTest.java"
Task: "Integration test deactivated account denial in backend/.../DeactivatedAccountLoginTest.java"
Task: "Vitest login/MFA component tests in frontend/.../login.component.spec.ts"
Task: "Playwright US1 acceptance scenarios in frontend/e2e/us1-login-rbac.spec.ts"

# Angular screens for User Story 1 (once services exist):
Task: "Login page in frontend/src/app/features/auth/login/login.component.ts"
Task: "MFA challenge screen in frontend/src/app/features/auth/login/mfa-challenge.component.ts"
Task: "Password reset screens in frontend/src/app/features/auth/password-reset/"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational (blocks all stories).
3. Complete Phase 3: User Story 1.
4. **STOP and VALIDATE**: run quickstart.md Scenario 1 independently.
5. Deploy/demo if ready — this alone delivers the constitution's core RBAC/RODO requirement for
   staff login.

### Incremental Delivery

1. Setup + Foundational → foundation ready.
2. US1 → validate independently (quickstart Scenario 1) → deploy/demo (MVP).
3. US2 → validate independently (quickstart Scenario 2) → deploy/demo.
4. US3 → validate independently (quickstart Scenario 3) → deploy/demo.
5. Polish phase → final SC-001–SC-007 verification.

### Parallel Team Strategy

With multiple developers, after Foundational is done:
- Developer A: User Story 1 (login/MFA/reset)
- Developer B: User Story 2 (audit log)
- Developer C: User Story 3 (account management)

Stories integrate independently since AuditLogEntry (written by US1/US3, read by US2) and
StaffAccount (owned by US3, read by US1) are both established in Foundational — no story blocks
another's own implementation work, only final end-to-end demos benefit from all three being done.

---

## Notes

- [P] tasks touch different files with no unmet dependencies.
- [Story] labels map every task to US1/US2/US3 for traceability back to spec.md.
- Tests MUST fail before their implementation task starts (Principle I, NON-NEGOTIABLE — no
  exception for this project).
- Frontend UI uses Angular Material defaults throughout — no custom branding/theme; that is
  explicitly deferred to a future feature (002) which will also refactor these screens.
- Commit after each task or logical group.
- Avoid: vague tasks, same-file conflicts, cross-story dependencies that break independent
  testability.

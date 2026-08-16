# Implementation Plan: Rejestracja i logowanie personelu z kontrolą dostępu opartą na rolach (RBAC)

**Branch**: `001-staff-auth-rbac` | **Date**: 2026-08-16 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-staff-auth-rbac/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

Staff-only authentication and role-based access control for the clinic system, covering three
roles (recepcja, lekarz, administrator) with least-privilege enforcement, mandatory TOTP-based
MFA, self-service password reset, admin-managed account lifecycle, and a tamper-evident,
append-only audit log for every login attempt and every permission change. This is the first
feature built in this repository (no application code exists yet), so it also establishes the
baseline Angular/Java project skeleton, the initial Postgres schema, and the minimal AWS/Terraform/
Helm/GitHub Actions scaffolding needed to deploy it — later features build on top of this
foundation rather than re-establishing it.

Technical approach: a Java (Spring Boot + Spring Security) backend exposes a REST API that is the
single authorization enforcement point (role checks happen server-side on every request, never
only in the Angular UI), backed by PostgreSQL (AWS RDS/Aurora) for accounts, roles, sessions, MFA
enrollment, password-reset tokens, and a hash-chained append-only audit log table. The Angular
frontend (mobile-first) provides login, MFA challenge, self-service password reset, and (for
administrators) account management and audit log review screens, deployed to Amazon EKS via
Helm/Argo CD from GitHub Actions, provisioned by Terraform.

## Technical Context

**Language/Version**: Backend: Java 25 (LTS). Frontend: TypeScript on Angular 21 (current LTS
release per Principle IV/Technology Stack Constraints).

**Primary Dependencies**: Backend: Spring Boot 4.1.x, Spring Security 7 (authentication,
method-level `@PreAuthorize` RBAC), Spring Data JPA, Flyway (schema migrations), `java-totp`
(RFC 6238 TOTP for MFA), Spring Session JDBC (shared session store across replicas). Frontend:
Angular (standalone components, Angular Router with route guards for UX-level redirects — not the
authorization source of truth), Angular Material or equivalent for accessible mobile-first UI
components.

**Build Tool**: Backend: Gradle (Kotlin DSL — `build.gradle.kts`/`settings.gradle.kts`), with the
Gradle Wrapper (`./gradlew`/`gradlew.bat`) committed to the repo and the
`org.gradle.toolchains.foojay-resolver-convention` plugin so the JDK 25 toolchain is
auto-provisioned — a contributor needs nothing pre-installed beyond a JVM to launch the wrapper
itself (research.md item 12). Frontend: npm/Angular CLI (`package-lock.json` committed).

**Storage**: PostgreSQL, hosted on AWS RDS for PostgreSQL or Aurora PostgreSQL (per Technology
Stack Constraints — never self-hosted). Single schema for this feature's tables (staff accounts,
roles, sessions, MFA enrollment, password-reset tokens, audit log); no additional datastore
(e.g. Redis/ElastiCache) introduced — session state also lives in Postgres via Spring Session JDBC
to avoid adding a new managed service for the first feature.

**Testing**: Backend: JUnit 5 (Jupiter) + Mockito + Spring Boot Test, with Testcontainers
(PostgreSQL) for integration tests against a real database (required by Principle I — Red before
Green); JUnit 5 is mandatory, not just preferred — Spring Boot 4.x dropped JUnit 4 support
entirely. Frontend: Vitest (Angular's current default unit test runner) for component/service
unit tests. End-to-end acceptance-scenario coverage (the login/RBAC/audit scenarios in spec.md)
via Playwright driving the Angular app against the real backend API — chosen over Cypress for
built-in free CI parallelization and broader (incl. WebKit) mobile-relevant browser coverage; see
research.md item 9.

**Target Platform**: Backend: containerized JVM service on Amazon EKS. Frontend: static Angular
build served from EKS-hosted service (behind the same ALB), mobile browsers as the primary target
per Principle IV, then tablet/desktop.

**Project Type**: Web application (Angular frontend + Java backend, per Technology Stack
Constraints) — this feature also creates the repository's first `backend/`, `frontend/`,
`infra/` (Terraform), `helm/`, and `.github/workflows/` trees, since none exist yet.

**Performance Goals**: Login-to-role-appropriate-home-screen in <10s (SC-001, includes MFA step).
Audit log entries visible to reviewers within 1 minute of the originating event (SC-003) — met
trivially by writing the audit entry synchronously in the same DB transaction as the event it
records, so no separate performance target is needed for this feature's expected clinic-scale
traffic (tens of staff accounts, not a high-throughput API).

**Constraints**: MFA mandatory for all three roles from day one (FR-015). Passwords and MFA
secrets encrypted at rest and in transit (FR-013) — TLS terminated at the AWS Load Balancer
Controller-managed ALB, Argon2id password hashing, encrypted MFA secret column (KMS-backed
encryption, consistent with Principle II's at-rest encryption requirement for sensitive
authentication data). Account lockout: 5 consecutive failed attempts → 15-minute lock (per spec
Assumptions). Session idle timeout: 15 minutes (per spec Assumptions). Password-reset link:
30-minute validity, single use (FR-016). Audit log entries MUST be append-only and tamper-evident
(FR-008) — enforced both at the application layer and at the database layer (see research.md).
RBAC-denied access MUST NOT reveal whether the target resource exists (FR-005).

**Scale/Scope**: Single clinic, single location (per spec Assumptions — no multi-tenant/multi-site
scoping in this feature). Expected tens of staff accounts, not thousands; this shapes storage and
performance choices toward simplicity over horizontal-scale optimization.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development (NON-NEGOTIABLE) | PASS | Backend (JUnit5/Testcontainers) and frontend (Vitest/Playwright) test tooling selected; `/speckit-tasks` MUST sequence a failing test before each implementation task. No violation to justify. |
| II. Patient Data Protection & RODO Compliance (NON-NEGOTIABLE) | PASS | This feature does not store or expose patient clinical data itself — it builds the RBAC boundary that later patient-data features must sit behind. Staff credentials and MFA secrets are sensitive authentication data and are encrypted at rest (Argon2id hashing, encrypted MFA secret column) and in transit (TLS at the ALB), matching Principle II's encryption bar even though this data is not itself Art. 9 special-category data. |
| III. Full Auditability | PASS | Central to this feature: FR-006/007/008 and User Story 2 implement exactly this principle (append-only, tamper-evident audit log of who/what/when/before-after, no edit/delete path through the app for any role). |
| IV. Mobile-First Design | PASS | All new Angular screens (login, MFA challenge, password reset, account management, audit log review) are designed mobile-first per Technical Context; desktop/tablet are progressive enhancements. |
| V. Risk-Tiered High Availability | PASS, with documentation obligation | Authentication/RBAC gates every other module (patient records, scheduling, billing are all high-risk per the constitution) — a failure here blocks all clinical operation, so it MUST be treated as high-risk itself. See "Risk Tier & Availability" below, as required before `/speckit-implement` may proceed for a high-risk module. |
| VI. Infrastructure & Delivery as Code (NON-NEGOTIABLE) | PASS | This is the first feature in the repo, so it also introduces the initial Terraform (VPC/EKS/RDS), Helm charts, Argo CD app definitions, and GitHub Actions workflows — all committed as code, no ClickOps. `/speckit-tasks` MUST enumerate this scaffolding as code-defined pipeline/infra tasks, not manual steps. |

No unjustified violations — Complexity Tracking table is empty.

### Risk Tier & Availability (Principle V documentation)

- **Module**: Staff Authentication & RBAC (this feature) — classified **high-risk**, on the same
  tier as patient records, scheduling, and billing, because every one of those modules depends on
  it to authorize every request; an outage here is an outage for all of them.
- **Failure domain isolation**: the auth service MUST be deployed as its own Deployment/Helm
  release (its own pod replica set, its own HPA) — never co-scheduled or bundled with lower-tier
  modules such as reporting or internal admin configuration UIs, so that a lower-tier module's
  resource exhaustion or crash cannot take down login/RBAC enforcement.
  Session state is stored in the same RDS/Aurora Postgres instance the auth service already
  requires, avoiding a second stateful dependency; RDS/Aurora Multi-AZ failover (standard AWS RDS
  capability) covers the database failure domain.
  Auth service pods run with ≥2 replicas across availability zones behind the ALB.
- **Consequence for later features**: any future feature that adds a lower-tier module (e.g.
  reporting) MUST deploy as a separate Helm release from `auth-service`, `patient-records`,
  `scheduling`, and `billing`, consistent with this boundary.

## Project Structure

### Documentation (this feature)

```text
specs/001-staff-auth-rbac/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md         # Phase 1 output (/speckit-plan command)
├── quickstart.md         # Phase 1 output (/speckit-plan command)
├── contracts/            # Phase 1 output (/speckit-plan command)
│   ├── auth-api.yaml
│   └── rbac-policy.md
└── tasks.md              # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

This feature establishes the repository's baseline layout (nothing below exists yet). It is a web
application per the constitution's Technology Stack Constraints (Angular frontend + Java backend),
so **Option 2 (Web application)** applies, plus the infra/delivery trees Principle VI requires:

```text
backend/
├── src/main/java/com/dentalclinic/auth/
│   ├── account/          # StaffAccount entity, repository, admin CRUD service (User Story 3)
│   ├── role/              # Role enum + permission matrix enforcement (User Story 1)
│   ├── session/            # Spring Session JDBC config, idle-timeout enforcement
│   ├── mfa/                # TOTP enrollment + challenge verification
│   ├── passwordreset/      # Self-service reset token issuance/consumption (FR-016/017)
│   ├── auditlog/           # Append-only, hash-chained audit log writer + read API (User Story 2)
│   └── api/                # REST controllers (see contracts/auth-api.yaml)
├── src/main/resources/db/migration/   # Flyway migrations (see data-model.md)
└── src/test/java/com/dentalclinic/auth/   # JUnit5 unit + Testcontainers integration tests

frontend/
├── src/app/features/auth/
│   ├── login/               # US1: credentials + MFA challenge
│   └── password-reset/      # US1: self-service reset request + confirm
├── src/app/features/admin/
│   ├── accounts/            # US3: create/deactivate/reactivate/role-assign
│   └── audit-log/           # US2: read-only audit log review
├── src/app/core/
│   ├── auth/                 # auth state, HTTP interceptor (attaches session), route guards (UX only)
│   └── rbac/                 # role-based UI visibility helpers (defense-in-depth is server-side, not here)
└── tests/                    # Vitest unit tests; e2e/ for Playwright acceptance-scenario tests

infra/terraform/               # VPC, EKS, RDS/Aurora Postgres, IAM — this feature's minimal baseline
helm/auth-service/             # Helm chart for the auth backend (own release, per Risk Tier section)
helm/frontend/                 # Helm chart for the Angular static frontend
.github/workflows/             # CI (build/test) + Terraform plan/apply + Helm/Argo CD sync, per Principle VI
```

**Structure Decision**: Option 2 (Web application: `backend/` + `frontend/`), extended with
`infra/terraform/`, `helm/`, and `.github/workflows/` because this is the first feature in an
otherwise-empty repository and Principle VI requires all infra/delivery to be code-defined from
the start rather than bolted on later. Backend code is organized by feature/domain
(account, role, session, mfa, passwordreset, auditlog) rather than by technical layer, matching
the feature's User Stories (US1 login/RBAC, US2 audit log, US3 account management) so each can be
implemented and tested independently per their "Independent Test" criteria in spec.md.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

*No violations — table intentionally empty.*

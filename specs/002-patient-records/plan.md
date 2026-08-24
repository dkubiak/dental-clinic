# Implementation Plan: Kartoteka pacjentów (dane podstawowe i stan uzębienia)

**Branch**: `002-patient-records` | **Date**: 2026-08-24 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-patient-records/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command; its definition describes the execution workflow.

## Summary

A patient-records module (kartoteka pacjentów): reception and doctor staff can create a patient
record with basic data (name, date of birth, address, optional PESEL); doctor and a new assistant
role can view and edit a simple binary (healthy/sick) per-tooth dental chart on a standard adult
jaw diagram; all staff can view a visit-history section that is a read-only placeholder until a
future, separately-specified visits module lands. This is the first feature to store Art. 9
special-category patient health data, so it extends 001's RBAC (new `ASSISTANT` role) and its
single append-only audit log (new patient-scoped event types) rather than introducing parallel
mechanisms.

Technical approach: a **new, independent Spring Boot deployable, `patient-service/`** (its own
top-level Gradle project, own Dockerfile, own Helm chart `helm/patient-service/`, own Deployment/
pod — mirroring how `backend/` is itself the standalone `auth-service` project, not a shared
"backend" umbrella), with its own `com.dentalclinic.patient` package (entities, repository,
service, REST API) and its own Flyway migration history. It connects to the *same* Postgres
instance 001 already provisions (not a new datastore — consistent with Technology Stack
Constraints and with 001's own precedent of not requiring a separate stateful dependency), but
under its own DB role (`patient_service_app`) and its own tables (`patient_record`,
`tooth_state`). It validates the caller's identity/role by reading the *same* Spring Session JDBC
session table 001 already writes (`spring_session`/`spring_session_attributes`), so no new
cross-service auth-token exchange is needed — session sharing works the same way 001's own
≥2 auth-service replicas already share sessions, just extended to a second service reading the
same table. `Role.java`/`staff_role` gains the new `ASSISTANT` value (in `auth-service`, since it
owns that enum); `audit_log_entry` (owned by `auth-service`) gains new event types and a
non-FK-constrained `target_patient_record_id` column so `patient-service` can write to the same
single audit trail without a cross-service database foreign key (research.md #5/#7). The Angular
frontend adds a `core/shell` module (persistent mobile-first toolbar/nav, replacing 001's
placeholder `RoleHomeComponent`) and a `features/patients` module (search/create, patient detail
with tabs for basic data, tooth chart, and the visit-history placeholder), talking to
`patient-service` through the same nginx/dev-proxy layer that already routes to `auth-service`.

## Technical Context

**Language/Version**: Backend: Java 25 (LTS) — unchanged from 001. Frontend: TypeScript on
Angular 21 (current LTS) — unchanged from 001.

**Primary Dependencies**: `patient-service` (new Gradle project, mirroring `auth-service`'s
`build.gradle.kts`): Spring Boot 4.1.x, Spring Security 7 (`@PreAuthorize`), Spring Data JPA,
Flyway, `spring-session-jdbc` (to *read* `auth-service`'s session table, not to issue sessions
itself), Testcontainers — same versions as `auth-service`, no AWS SDK/TOTP dependency needed
(no MFA/email work happens in this service). `auth-service` itself gains no new dependency, only
new migrations/enum values. Frontend: Angular Material (already in use per
`role-home.component.ts`) for toolbar/nav/forms; the jaw/tooth chart is a hand-built inline SVG
component (32-tooth adult FDI layout) — no charting/diagram library is justified for a fixed,
simple binary-state diagram.

**Storage**: PostgreSQL (AWS RDS/Aurora per Technology Stack Constraints) — the *same* instance/
database 001 already provisions (`dental_clinic_auth`), not a new datastore, but a **new DB role**
`patient_service_app` (research.md #7) owning two new tables: `patient_record`, `tooth_state`.
`auth-service`-owned objects extended: `staff_role` enum (+`ASSISTANT`), `audit_event_type` enum
(+patient-scoped event types), `audit_log_entry` (+nullable `target_patient_record_id UUID`
column, **no DB-level FK** — see research.md #5 for why a cross-service foreign key is
deliberately avoided) — one audit trail for the whole system rather than a second, parallel one.

**Testing**: Unchanged from 001 — JUnit 5 + Mockito + Spring Boot Test + Testcontainers
(PostgreSQL) for backend; Vitest for frontend unit tests; Playwright for the acceptance scenarios
in spec.md (patient creation, tooth-chart edit, RBAC denial, search, visit-history placeholder).

**Target Platform**: Same as 001 — containerized JVM service(s) on Amazon EKS, static Angular
build behind the same ALB. `patient-service` is its **own** Deployment/Helm release, not
co-scheduled with `auth-service`. See Risk Tier & Availability below.

**Project Type**: Web application (Angular + Java). Adds a **new top-level Gradle project**,
`patient-service/`, alongside the existing `backend/` (`auth-service`) and `frontend/` — matching
the repo's existing pattern of one top-level directory per independently deployable unit, not a
multi-module restructure of `backend/`.

**Performance Goals**: Patient-record creation completes in <2 min of staff time (SC-001, a UX/
form-design target, not a backend latency target). Tooth-state toggle reflected in <15s from
opening the record (SC-002) — trivially met by a synchronous single-row UPDATE + optimistic UI
update. Patient search returns in <10s of staff time (SC-004) — a simple indexed `ILIKE`/exact-
PESEL lookup against a table sized for one clinic's patient list is not a scaling concern at this
stage.

**Constraints**: PESEL is optional; when present it MUST pass Poland's standard 11-digit
weighted-checksum validation server-side (authoritative) with client-side mirroring for UX only
(research.md #1) — same "server is the source of truth" rule as 001. PESEL uniqueness enforced via
a partial unique index (`WHERE pesel IS NOT NULL`) — no fuzzy-matching duplicate detection for
PESEL-less patients (spec Assumptions, accepted risk). Tooth chart: 32-tooth adult permanent
dentition (FDI/ISO 3950 numbering), binary state only (`HEALTHY`/`SICK`), pre-created at record
creation time so "new record ⇒ all teeth healthy" needs no null-handling (research.md #3).
Patient data (contact + dental state) is Art. 9 special-category data: encrypted at rest and in
transit via the same mechanisms 001 already established (RDS/Aurora KMS-backed storage encryption,
TLS at the ALB) — no new column-level encryption is introduced, since PESEL/dental state do not
carry the same "single irrecoverable secret" risk profile that justified MFA-secret column
encryption in 001. RBAC: `RECEPTION`/`DOCTOR` create/edit basic data; `DOCTOR`/`ASSISTANT` view/
edit the tooth chart; `RECEPTION`/`DOCTOR` view the visit-history placeholder; `ASSISTANT` gets
read-only access to basic data (identification only). RODO export/erasure (FR-009/FR-010) is
scoped to `DOCTOR` only, not `ADMINISTRATOR` — see Constitution Check (Principle II) and
research.md #6 for why administrator access is deliberately not extended to clinical data.
Cross-service session validation: `patient-service` reads (never writes/invalidates)
`auth-service`'s `spring_session`/`spring_session_attributes` rows to authenticate a request and
resolve the caller's role — the DB grant for `patient_service_app` on those tables is
`SELECT, UPDATE` only (UPDATE solely to bump `last_access_time`, matching Spring Session JDBC's
own read-path behavior), never `INSERT`/`DELETE` (research.md #7). The audit-log hash chain
(`AuditLogWriter`, 001) currently serializes concurrent writers with an in-process JVM
`synchronized` block, which cannot serialize writes coming from a *second service's* JVM (or even
correctly serialize `auth-service`'s own ≥2 replicas) — this feature MUST change it to a
Postgres advisory lock (`pg_advisory_xact_lock`) so the "read tail, compute hash, insert" sequence
is atomic across every writer regardless of process/replica (research.md #5a). This is a
modification to already-shipped 001 code, flagged per CLAUDE.md governance guidance.

**Scale/Scope**: Same single clinic, tens-to-low-hundreds of patients — no multi-tenant/multi-site
scoping (consistent with 001's Scale/Scope assumption).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|---|---|---|
| I. Test-First Development (NON-NEGOTIABLE) | PASS | Same backend/frontend test stack as 001 (JUnit5/Testcontainers, Vitest, Playwright); `/speckit-tasks` MUST sequence a failing test before each implementation task. |
| II. Patient Data Protection & RODO Compliance (NON-NEGOTIABLE) | PASS, with an explicit RBAC decision **and a documented erasure-scope exception** | First feature storing Art. 9 patient health data. RBAC scoped per job function (RECEPTION: contact data only; DOCTOR/ASSISTANT: + dental chart), encrypted at rest/in transit via 001's existing RDS KMS + TLS. FR-009/FR-010 (export/erasure) are deliberately scoped to `DOCTOR` only — **not** `ADMINISTRATOR` — to avoid silently violating `rbac-policy.md` rule 3 ("No implicit admin clinical access... requires an explicit spec/constitution-level decision, not a quiet code change"). This feature makes that decision explicitly rather than defaulting administrator into a clinical-data workflow. **Erasure-completion scope exception**: this feature implements the erasure *request* workflow end-to-end (accept, RBAC-gate to `DOCTOR`, audit-log via `PATIENT_DATA_ERASURE_REQUESTED`); the actual anonymization/deletion execution — and the `PATIENT_DATA_ERASURE_COMPLETED` event it emits — is deliberately deferred to a follow-up feature, because Polish medical-record retention periods (years, statutorily defined per record type) mean no single "done" completion point exists at feature-implementation time. Principle II's "before...done" clause is read here as requiring the erasure *workflow* to exist and be correctly gated/audited, not that every patient's individual retention period must already have elapsed. This is a reviewed, intentional interpretation (see tasks.md T060), not a silent gap. |
| III. Full Auditability | PASS | Every patient-record and tooth-state create/**read**/update logged to the *same* `audit_log_entry` table 001 created (new event types incl. `PATIENT_RECORD_VIEWED`/`TOOTH_CHART_VIEWED` for read coverage per FR-007/SC-003, + new nullable target column), preserving one audit trail instead of fragmenting it (research.md #5). |
| IV. Mobile-First Design | PASS | New shell/nav, patient search/create forms, and the tooth-chart SVG are all designed mobile-first (bottom-nav/FAB on phone, expanded toolbar/side-nav on desktop) per the UX discussion preceding this plan. |
| V. Risk-Tiered High Availability | PASS | Patient records is high-risk, same tier as Staff Auth/RBAC (001). This feature ships as its **own** Spring Boot deployable (`patient-service/`) with its **own** Helm release/Deployment (`helm/patient-service/`, ≥2 replicas), never co-scheduled with `auth-service` — see Risk Tier & Availability below. |
| VI. Infrastructure & Delivery as Code (NON-NEGOTIABLE) | PASS | New Terraform (ECR repo, IRSA role for `patient-service`, if any AWS access is needed later) and Helm (`helm/patient-service/`, mirroring `helm/auth-service/`) resources are added as code, via the same GitHub Actions pipeline (new CI job for `patient-service`, alongside the existing `backend`/`frontend-*` jobs) — no ClickOps. Still not live against real AWS infra (deploy/terraform CI intentionally red per prior project decision); this feature's CI *build/test* job is not deferred, only the deploy/apply steps are. |

**Cross-feature dependency (flagged per CLAUDE.md governance guidance, not a silent change)**:
this feature requires modifying already-shipped 001 code: `Role.java` (+`ASSISTANT`), the
`staff_role` Postgres enum, `rbac-policy.md`'s permission matrix, `role.guard.ts` usage, and
`app.routes.ts`. The constitution's Principle II role list is illustrative ("e.g. recepcja,
lekarz, administrator"), not closed, so adding a role is not a constitutional amendment — but it
does touch auth/authz code and therefore falls under the "explicit security/compliance review
before merge" gate (Development Workflow & Quality Gates), same as any patient-data change.

### Risk Tier & Availability (Principle V documentation)

- **Module**: Patient Records — classified **high-risk**, same tier as Staff Auth/RBAC (001),
  Scheduling, and Billing, per the constitution.
- **Decision for this feature**: `patient-service` is a genuinely separate Spring Boot deployable
  with its **own** Helm release (`helm/patient-service/`, own Deployment, own Service, own HPA,
  ≥2 replicas across AZs behind the ALB — mirroring `helm/auth-service/values.yaml`'s
  `replicaCount: 2`/`autoscaling` block) and its own container image/ECR repo. It is **never**
  co-scheduled with `auth-service`, satisfying the same failure-domain isolation 001 already
  applies to itself.
- **Shared dependency, and why that's still consistent with Principle V**: both services connect
  to the *same* RDS/Aurora Postgres instance — this mirrors 001's own already-accepted precedent
  ("Session state is stored in the same RDS/Aurora Postgres instance the auth service already
  requires, avoiding a second stateful dependency; RDS/Aurora Multi-AZ failover ... covers the
  database failure domain" — 001 plan.md). Principle V's failure-domain requirement is about
  **compute** isolation (a crash/resource-exhaustion in one module must not take down another),
  which a separate Deployment/pod already provides; sharing the managed, Multi-AZ database
  instance does not reintroduce a compute failure domain, and provisioning a second RDS/Aurora
  instance for a single-clinic-scale system would be cost/ops overhead with no HA benefit this
  constitution asks for.
- **Session sharing across services**: `patient-service` authenticates requests by reading the
  *same* Spring Session JDBC table `auth-service` writes (`spring_session`/
  `spring_session_attributes`) — the identical mechanism that already lets `auth-service`'s own
  ≥2 replicas share sessions, just read by a second service's DB role
  (`patient_service_app`, grant: `SELECT, UPDATE` only — research.md #7). No new token-exchange
  protocol or shared secret is introduced.
- **Audit trail**: both services write to the one `audit_log_entry` table `auth-service` owns
  (research.md #5), which requires fixing `AuditLogWriter`'s hash-chain concurrency control
  (currently an in-process `synchronized`, insufficient across processes) to a Postgres advisory
  lock (research.md #5a) — a necessary, explicitly-flagged change to already-shipped 001 code.
- **What this buys**: a genuinely separate failure domain today (not deferred), consistent with
  001's own commitment that "any future feature that adds a lower-tier module ... MUST deploy as a
  separate Helm release from auth-service, patient-records, scheduling, and billing" — this
  feature holds itself to the same bar those future lower-tier modules will be held to.

## Project Structure

### Documentation (this feature)

```text
specs/002-patient-records/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   └── patient-api.yaml
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

Extends the existing web-application layout from 001 (Option 2: `backend/` + `frontend/`), plus
a **new top-level Gradle project**, `patient-service/`, its own Helm chart, and its own
docker-compose/nginx wiring — mirroring exactly how `backend/` (`auth-service`) and `frontend/`
already each get their own directory, Helm chart, and compose service block:

```text
backend/                          # UNCHANGED layout, MODIFIED contents (auth-service)
├── src/main/java/com/dentalclinic/auth/
│   └── role/                     # MODIFIED: Role enum + ASSISTANT
├── src/main/java/com/dentalclinic/auth/auditlog/
│   └── AuditLogWriter.java       # MODIFIED: synchronized → pg_advisory_xact_lock (research.md #5a)
└── src/main/resources/db/migration/   # NEW migrations: ASSISTANT enum value, new audit_event_type
                                        # values, audit_log_entry.target_patient_record_id column,
                                        # idempotent CREATE ROLE + GRANT for patient_service_app

patient-service/                  # NEW top-level Gradle project (own gradlew, own Dockerfile,
│                                  #  mirrors backend/'s build.gradle.kts minus AWS SDK/TOTP deps)
├── src/main/java/com/dentalclinic/patient/
│   ├── record/                   # PatientRecord entity, repository, create/search/edit service (US1)
│   ├── toothchart/                # ToothState entity, repository, view/edit service (US2)
│   ├── visithistory/               # Read-only placeholder endpoint (US3)
│   ├── session/                     # Reads auth-service's spring_session tables (research.md #7)
│   └── api/                          # REST controllers (see contracts/patient-api.yaml)
├── src/main/resources/db/migration/   # OWN Flyway history (own flyway table), creates
│                                       # patient_record/tooth_state + patient_service_app role
└── src/test/java/com/dentalclinic/patient/   # JUnit5 unit + Testcontainers integration tests

frontend/
├── src/app/core/
│   ├── shell/                 # NEW: persistent mobile-first toolbar/nav shell (replaces
│   │                          #      RoleHomeComponent's placeholder body; auth/rbac unchanged)
│   └── rbac/                  # MODIFIED: add ASSISTANT to UI-visibility helpers
├── src/app/features/patients/  # NEW module for this feature
│   ├── patient-search/         # US1: find existing patient (FR-012)
│   ├── patient-create/         # US1: new-patient form (FAB entry point, FR-001/002)
│   ├── tooth-chart/             # US2: jaw SVG + per-tooth state toggle (FR-005/006)
│   └── visit-history/           # US3: read-only placeholder (FR-004)
├── nginx.conf                     # MODIFIED: new `location /patients` block → patient-service:8080
├── proxy.conf.json                # MODIFIED: new "/patients" dev-proxy entry → patient-service
└── tests/                        # Vitest unit tests; e2e/ additions for Playwright

helm/
└── patient-service/               # NEW chart, mirrors helm/auth-service/ (Chart.yaml, values.yaml
                                    #  with replicaCount: 2 + autoscaling, templates/)

docker-compose.yml                 # MODIFIED: new `patient-service` service block (own DB_USERNAME
                                    #  patient_service_app, depends_on postgres + implicitly
                                    #  auth-service's migrations having run first for shared grants)

.github/workflows/ci.yml           # MODIFIED: new `patient-service` job mirroring the existing
                                    #  `backend` job (JDK 25 setup, ./gradlew build --no-daemon)
```

**Structure Decision**: Option 2 (Web application) extended with a **second, independent backend
deployable** — `patient-service/` sits alongside `backend/` and `frontend/` as its own top-level
project, exactly matching the repo's existing one-directory-per-deployable convention (confirmed
by `docker-compose.yml`'s own header comment: "every new backend service ... gets its own service
block here, named to match its Helm chart"). No multi-module restructuring of the existing,
already-shipped `backend/` project.

## Complexity Tracking

> Fill ONLY if Constitution Check has violations that must be justified

*No violations — table intentionally empty. Two services sharing one Postgres instance (for
session validation and the single audit trail) is not a Principle V violation; it mirrors 001's
own already-accepted precedent that shared, Multi-AZ-managed database state does not create a
shared compute failure domain (see Risk Tier & Availability above).*

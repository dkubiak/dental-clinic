# Phase 0 Research: Kartoteka pacjentów

All items below resolve a design choice not fully pinned down by spec.md; none are open
`NEEDS CLARIFICATION` markers (spec.md's three clarifications are already resolved).

## 1. PESEL validation

- **Decision**: Validate PESEL format (11 digits) and checksum (standard Polish weighted-sum
  algorithm: weights `1,3,7,9,1,3,7,9,1,3` applied to digits 1–10, mod 10, compared against
  digit 11) server-side, in the same request that creates/edits a patient record. The frontend
  mirrors the same check for immediate form feedback, but the backend check is authoritative —
  same "server is the source of truth" rule 001 established for RBAC.
- **Rationale**: Pure, well-known, deterministic algorithm — no external validation service or
  library needed.
- **Alternatives considered**: Client-only validation (rejected — a UI-only check can be
  bypassed by direct API calls, same reasoning as 001's RBAC enforcement); calling an external
  PESEL-verification API (rejected — the checksum is self-contained arithmetic, no external
  dependency is justified for it).

## 2. Duplicate-patient detection

- **Decision**: A partial unique index on `patient_record.pesel` (`WHERE pesel IS NOT NULL`)
  is the only automated duplicate check. No name/date-of-birth fuzzy matching in this version.
- **Rationale**: Matches spec.md's explicit, user-accepted scope decision — PESEL-less patients
  are not automatically deduplicated (documented as an accepted risk in spec.md Edge Cases).
- **Alternatives considered**: Trigram/fuzzy name+DOB matching to flag possible duplicates even
  without PESEL — rejected for this iteration; would be a reasonable follow-up but was not asked
  for and adds meaningful complexity (similarity thresholds, false-positive handling, review UX).

## 3. Tooth-chart data representation

- **Decision**: On patient-record creation, pre-create all 32 `tooth_state` rows (FDI/ISO 3950
  numbering, permanent adult dentition: 11–18, 21–28, 31–38, 41–48), each defaulting to
  `HEALTHY`. Edits are plain row `UPDATE`s; state history is reconstructed from the audit log
  (research.md #5), not from a versioned tooth-state table.
- **Rationale**: Directly satisfies spec.md US2 Acceptance Scenario 3 ("nowo założona kartoteka
  ⇒ wszystkie zęby domyślnie zdrowe") without null-handling in the UI, and keeps the schema
  trivial to extend later (spec.md explicitly defers colors/descriptions/disease codes to a
  future iteration — adding columns to this same row shape will be enough then).
- **Alternatives considered**: Sparse storage (only insert a row when a tooth first becomes
  non-default) — rejected, pushes "is this tooth healthy by default" null-handling into every
  read path for no benefit at this scale (32 rows/patient is negligible). A fully versioned
  tooth-history table — rejected as premature; the audit log already captures before/after state
  per change, which is sufficient until the richer future iteration is actually specified.

## 4. RBAC role extension (`ASSISTANT`)

- **Decision**: Add a fourth role, `ASSISTANT`, to 001's `staff_role` Postgres enum and
  `Role.java`, scoped to: read-only basic patient data (for identification) + read/write tooth
  chart. Update `rbac-policy.md`'s permission matrix, `role.guard.ts`, and `app.routes.ts`
  accordingly, as a modification to already-shipped 001 code.
- **Rationale**: The constitution's Principle II role list is explicitly illustrative
  ("e.g. recepcja, lekarz, administrator"), not a closed enumeration, so adding a role is a
  plan-level decision, not a constitutional amendment. The user explicitly asked for a distinct
  assistant persona who can edit the tooth chart alongside the doctor, not for reusing
  `RECEPTION` (spec.md Clarifications session 2026-08-24).
- **Alternatives considered**: Modeling "asystent/asystentka" as a permission flag on the
  existing `RECEPTION` role — rejected; the spec.md clarification explicitly rejected treating
  recepcja as able to view/edit medical data, since that breaks the least-privilege boundary
  001 already established (rbac-policy.md: "recepcja: ... No clinical/medical data").

## 5. Audit trail for patient data

- **Decision**: Extend the *existing* `audit_log_entry` table (append-only, hash-chained,
  `INSERT`/`SELECT`-only grants — 001's `V5__audit_log.sql`, owned by `auth-service`) with new
  `audit_event_type` enum values (e.g. `PATIENT_RECORD_CREATED`, `PATIENT_RECORD_UPDATED`,
  `TOOTH_STATE_CHANGED`) and a new nullable `target_patient_record_id UUID` column, rather than
  creating a second, parallel audit table for clinical data. **Now that `patient-service` is a
  separate deployable (revised after user feedback — see #7), this column is deliberately NOT a
  DB-level foreign key** to `patient_record.id` — `patient_record` is owned by a different
  service's migration history, so a hard FK would couple the two services' schema-migration
  ordering (whichever runs first would need the other's table to already exist). Referential
  integrity for this column is enforced at the application layer only (same pattern commonly used
  for cross-service references), consistent with `patient-service` and `auth-service` being
  genuinely independent deployables now.
- **Rationale**: The constitution's Full Auditability principle describes one property of the
  system ("every create/read/update/delete ... append-only ... tamper-evident"), not one per
  data domain. A single audit trail is simpler to review for compliance and reuses 001's
  already-tested tamper-evidence mechanism (hash chain + revoked `UPDATE`/`DELETE` grants), now
  extended (research.md #5a) to be safe for multiple writers.
- **Alternatives considered**: A separate `patient_audit_log_entry` table scoped to this feature
  — rejected; would fragment the audit trail across two tables with two hash chains for no
  compliance benefit. A hard cross-service FK on `target_patient_record_id` — rejected once the
  services split (see #7); would make each service's schema migrations depend on the other's
  deployment/migration order, which is exactly the kind of coupling separating them was meant to
  avoid.

## 5a. Audit hash-chain concurrency across two services

- **Decision**: Replace `AuditLogWriter`'s in-process `synchronized` block (001,
  `backend/src/main/java/com/dentalclinic/auth/auditlog/AuditLogWriter.java`) with a Postgres
  session/transaction advisory lock (`pg_advisory_xact_lock(<fixed key>)`) held for the duration
  of "read current chain tail → compute new hash → insert", released automatically at transaction
  commit.
- **Rationale**: The existing `synchronized` keyword only serializes threads *within one JVM*. It
  was already an unaddressed gap for `auth-service`'s own ≥2 replicas (each replica is a separate
  JVM); adding `patient-service` as a second *service* writing to the same table makes the gap
  unambiguous and unavoidable to fix now, since two genuinely different processes (potentially on
  different nodes) can otherwise both read the same "latest" row and compute conflicting chains.
  A Postgres advisory lock is coordinated by the database itself, so it correctly serializes
  writers regardless of which process or service they run in — no new infrastructure (e.g.
  distributed lock service) is introduced.
- **Alternatives considered**: A `SELECT ... FOR UPDATE` against a sentinel row — workable, but an
  advisory lock is simpler here since there's no natural single row to lock (the "tail" is
  whichever row has the highest `id`, which itself becomes the row being contended over). Moving
  hash computation into a DB trigger/function — would also solve the concurrency problem, but was
  not chosen because it would relocate compliance-critical logic (`AuditEntryHash.compute`) out of
  version-controlled, unit-testable application code into a migration file with weaker test
  tooling; the advisory-lock approach keeps `AuditEntryHash` exactly as it is and only changes how
  the read-then-insert sequence is synchronized.

## 6. RODO export/erasure ownership (FR-009/FR-010)

- **Decision**: The `DOCTOR` role executes patient data export and erasure/anonymization
  requests. `ADMINISTRATOR` is **not** granted access to this workflow in this feature.
- **Rationale**: `rbac-policy.md` (001) states explicitly: "ADMINISTRATOR has zero rows granting
  patient-data access ... any future change granting administrators clinical-data access requires
  an explicit spec/constitution-level decision, not a quiet code change." A RODO export of a full
  patient record necessarily includes clinical data (dental state), so routing it through
  `ADMINISTRATOR` — a common default in other systems — would silently violate that rule. `DOCTOR`
  is the only existing role with full read access to both contact and clinical data, so it is the
  natural owner without any RBAC-policy change.
- **Alternatives considered**: `ADMINISTRATOR` owns subject-rights workflows (common pattern
  elsewhere) — rejected per the explicit rule above; would need its own constitution/spec-level
  decision to revisit, which is out of scope for this feature. Splitting export into a
  "non-clinical part via ADMINISTRATOR + clinical part via DOCTOR" — rejected as unnecessary
  complexity for a single-clinic-scale system; one combined `DOCTOR`-owned export satisfies
  FR-009 without a two-step workflow.

## 7. Deployment topology for this feature

- **Decision** (revised after explicit user direction — patient-records must not share a
  Deployment with auth-service): `patient-service` is a new, independent Spring Boot/Gradle
  project (its own top-level directory, own Dockerfile, own `helm/patient-service/` chart, own
  Deployment/pod, ≥2 replicas), matching how `backend/` (`auth-service`) is already its own
  standalone project (`settings.gradle.kts`: `rootProject.name = "auth-service"`), not a shared
  "backend" umbrella. It connects to the *same* Postgres instance as `auth-service` (no new RDS/
  Aurora instance — see plan.md Risk Tier & Availability for why that's still Principle V-
  compliant), under its own DB role `patient_service_app`.
- **Session validation across services**: `patient-service` authenticates a request by reading
  the *same* `spring_session`/`spring_session_attributes` tables `auth-service` already writes via
  Spring Session JDBC (`V3__session.sql`), deserializing the same Spring Security session
  attribute both services already understand (same Spring Boot/Session versions). This is the
  same mechanism that already lets `auth-service`'s own ≥2 replicas share sessions — extended to a
  second service's DB role, not a new protocol. `patient_service_app`'s grant on those tables is
  `SELECT, UPDATE` only (never `INSERT`/`DELETE` — session lifecycle remains exclusively
  `auth-service`'s responsibility).
- **Migration ordering safety**: `patient-service` owns its own Flyway migration history (its own
  Flyway schema-history table) but needs `auth-service` to eventually grant it access to
  `spring_session*` and `audit_log_entry`, and `auth-service` needs `patient_service_app` to
  exist before it can grant to it. Both migration sets use the idempotent
  `DO $$ ... IF NOT EXISTS (SELECT FROM pg_roles ...) THEN CREATE ROLE ... END IF; END $$;`
  pattern 001 already established for `auth_service_app`/`auth_service_retention`
  (`V5__audit_log.sql`, `V8__audit_log_retention_role.sql`), so either service's migrations can run
  first without erroring — whichever runs second just adds its grants to a role that may or may
  not already exist.
- **Rationale**: Directly satisfies Principle V's failure-domain requirement (own Deployment, own
  pod, own failure domain) rather than deferring it — see plan.md Risk Tier & Availability for the
  full reasoning on why sharing the Postgres instance itself does not undermine that.
- **Alternatives considered** (the previous decision in this document, before user feedback):
  shipping inside the existing backend monolith/Helm release, justified by the project's infra/
  deploy pipeline not being live yet — rejected on reconsideration: Principle V's failure-domain
  requirement doesn't depend on production traffic existing yet, and building the service
  boundary now (while the code is still new) is cheaper than retrofitting it after more code
  accumulates inside a shared deployable.

## 8. Frontend module/shell structure

- **Decision** (carried over from prior discussion with the user): a single, persistent,
  mobile-first `AppShellComponent` (`core/shell`) hosts a top toolbar + role-aware nav (bottom-nav
  on mobile, expanded toolbar/side-nav on desktop) wrapping lazy-loaded feature routes. Each
  business domain gets its own lazy-loaded Angular route/module under `features/` (`patients` now,
  `visits` later, `admin` already existing from 001) — no domain module imports another domain
  module directly; only the shell and `core/auth`/`core/rbac` are shared.
- **Rationale**: Satisfies Principle IV (mobile-first) and gives Principle V's module-boundary
  documentation a matching frontend structure (no cross-domain coupling in the UI layer either),
  without over-building (no micro-frontends — a single Angular app with lazy routes is sufficient
  at this project's scale).
- **Alternatives considered**: Per-role landing pages with no shared shell (today's 001
  `RoleHomeComponent` placeholder) — rejected, doesn't scale past one entry point per role and
  duplicates nav/toolbar code per role. Micro-frontends (separately deployed per domain) —
  rejected as premature for a single small Angular app.

## 9. "Nowy pacjent" primary-action placement

- **Decision**: A FAB (mobile) / prominent toolbar button (desktop) for "Nowy pacjent", persistent
  across whichever view is active (patient list now, later also the visits calendar) — the same
  "global primary action" pattern as an email client's "Compose" button, not a button embedded in
  the patient list view.
- **Rationale**: Matches spec.md US1 (reception/doctor's most frequent action) and keeps the
  action stable once the visits/calendar module becomes the default landing view, avoiding a nav
  rework at that point.
- **Alternatives considered**: A button inside the patient-list view only — rejected, would need
  to move/duplicate once the calendar becomes the default landing screen.

## Address representation

- **Decision**: Structured fields (street, building/apartment number, postal code, city) rather
  than one free-text field.
- **Rationale**: Matches common Polish address entry UX, and keeps future search/filter/export
  work (FR-009) straightforward.
- **Alternatives considered**: Single free-text address field — rejected, harder to validate and
  to filter/search on later.

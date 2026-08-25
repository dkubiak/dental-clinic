# Quickstart: Validating Kartoteka pacjentów

This guide proves the feature works end-to-end against the design in `data-model.md` and
`contracts/patient-api.yaml`. It does not contain implementation code — see `tasks.md` (generated
by `/speckit-tasks`) for that.

## Prerequisites

- Local PostgreSQL (or Testcontainers-managed instance) with **both** services' migrations
  applied: `auth-service`'s new migrations (`staff_role` +`ASSISTANT`, `audit_event_type`
  extensions, `audit_log_entry.target_patient_record_id`, idempotent `patient_service_app` role
  creation/grants) and `patient-service`'s own migrations (`patient_record`, `tooth_state`,
  `patient_service_app` role creation if it didn't already exist — research.md #7 covers why
  either order works).
- **Two** backend services running locally: `auth-service` (`backend/`, unchanged endpoints) and
  the new `patient-service` (own container/process, own port), both pointed at the same Postgres
  instance.
- Frontend (`frontend/`) running locally, with nginx/proxy.conf.json routing `/patients` to
  `patient-service` alongside the existing `/auth`, `/accounts`, `/audit-log` routes to
  `auth-service`, including the new `core/shell` + `features/patients` UI.
- Seed data: four staff accounts, one per role (extends 001's three with the new role), each with
  MFA enrollment already completed:
  - `reception@clinic.test` / role `RECEPTION`
  - `doctor@clinic.test` / role `DOCTOR`
  - `assistant@clinic.test` / role `ASSISTANT`
  - `admin@clinic.test` / role `ADMINISTRATOR`

## Scenario 0 — Cross-service session sharing (plan.md Risk Tier & Availability, research.md #7)

1. Log in via `auth-service` as `doctor@clinic.test` (`/auth/login` + `/auth/mfa/verify`),
   obtaining a session cookie.
2. Using that same session cookie, call `patient-service` directly (e.g. `GET /patients?q=Kowal`).
   **Expect**: `200` — `patient-service` authenticates the request from the shared session table
   without a second login, proving session sharing works across the two independent deployables.
3. Stop/restart the `patient-service` process (simulating a pod restart) without touching
   `auth-service` or the session.
   **Expect**: the same session cookie still works against `patient-service` once it's back up —
   no session state was lost, because it never lived in `patient-service`'s own memory.

## Scenario 1 — Create a new patient record (User Story 1, P1)

Maps to spec.md Acceptance Scenarios 1–6 under User Story 1.

1. Log in as `reception@clinic.test`. From the shell's "Nowy pacjent" FAB/toolbar button, submit
   the form with first name, last name, date of birth, address, and a valid PESEL.
   **Expect**: `201`, record appears in patient search.
2. Log in as `doctor@clinic.test`; repeat step 1 with different data.
   **Expect**: same success path as reception.
3. Submit the form with a PESEL that fails the checksum (e.g. last digit altered).
   **Expect**: `400`, no record created.
4. Submit the form with a PESEL that already exists on another record.
   **Expect**: `409`, no duplicate record created.
5. Submit the form with all required fields except PESEL (left blank).
   **Expect**: `201` — record created without automatic duplicate detection (accepted risk).
6. Log in as `assistant@clinic.test`; attempt to open the "Nowy pacjent" form / call `POST
   /patients` directly.
   **Expect**: `404` (assistant is not RECEPTION/DOCTOR).

## Scenario 2 — Tooth-chart view/edit (User Story 2, P2)

Maps to spec.md Acceptance Scenarios under User Story 2.

1. As `doctor@clinic.test`, open a newly created patient's tooth chart.
   **Expect**: all 32 teeth shown as `HEALTHY`.
2. Mark tooth `26` as `SICK` (`PATCH /patients/{id}/tooth-chart/26`).
   **Expect**: `200`, chart reflects `SICK` for tooth 26; an audit log entry
   (`TOOTH_STATE_CHANGED`, before=`HEALTHY`, after=`SICK`) is recorded.
3. As `assistant@clinic.test`, mark the same tooth back to `HEALTHY`.
   **Expect**: `200`; a second audit entry recorded, attributed to the assistant's account.
4. As `reception@clinic.test`, attempt `GET`/`PATCH` on the same tooth-chart endpoints.
   **Expect**: `404` (reception has no tooth-chart access, per FR-006/rbac-policy.md).

## Scenario 3 — Visit-history placeholder (User Story 3, P3)

Maps to spec.md Acceptance Scenarios under User Story 3.

1. As `reception@clinic.test` and separately as `doctor@clinic.test`, open the visit-history
   section for any patient.
   **Expect**: `200` with an empty array in both cases; no "add entry" affordance is present in
   the UI (no corresponding write endpoint exists).
2. As `assistant@clinic.test`, request `GET /patients/{id}/visit-history` directly.
   **Expect**: `404` (assistant is not RECEPTION/DOCTOR for this resource).

## Scenario 4 — Search (FR-012)

1. As `reception@clinic.test`, search `GET /patients?q=Kowal` (partial last name).
   **Expect**: all matching records returned within the seeded data.
2. Search `GET /patients?q=<exact PESEL>`.
   **Expect**: exactly the one matching record, if a PESEL was recorded for it.

## Scenario 5 — RODO export/erasure ownership (research.md #6)

1. As `doctor@clinic.test`, call `POST /patients/{id}/export`.
   **Expect**: `200` with basic data + tooth chart (+ empty visit history).
2. As `admin@clinic.test`, call the same endpoint.
   **Expect**: `404` — administrator deliberately has no clinical-data export access
   (rbac-policy.md rule 6).
3. As `doctor@clinic.test`, call `POST /patients/{id}/erasure-request`.
   **Expect**: `202`; a `PATIENT_DATA_ERASURE_REQUESTED` audit entry is recorded.

## Scenario 6 — Audit trail integrity across two writers (Principle III, research.md #5/#5a)

1. Perform one create, one basic-data edit, and one tooth-state change from Scenarios 1–2 (all
   written by `patient-service`), interleaved with one login from Scenario 0 (written by
   `auth-service`).
   **Expect**: all corresponding rows land in the *same* `audit_log_entry` table `auth-service`
   created, each patient-scoped row with `target_patient_record_id` populated and
   `target_account_id` null (and vice versa for the login row), forming one unbroken hash chain
   across both services' writes (no separate audit table/chain introduced).
2. Fire several tooth-state changes concurrently (e.g. two parallel requests from `doctor` and
   `assistant` sessions against different teeth of the same patient) against `patient-service`.
   **Expect**: no chain-verification failure (`AuditHashChainVerifier`, 001) — proves the
   `pg_advisory_xact_lock` fix (research.md #5a) correctly serializes concurrent writers, unlike
   the original in-process `synchronized`, which could not have coordinated across two services.
3. Attempt (as any role, including `ADMINISTRATOR`) to `UPDATE`/`DELETE` any of those rows via any
   application path, from either service.
   **Expect**: no such path exists in either service — `patient_service_app`'s grant on
   `audit_log_entry` is `SELECT, INSERT` only, same tamper-evidence boundary as `auth_service_app`.

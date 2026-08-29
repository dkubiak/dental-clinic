# Phase 0 Research: Historia medyczna pacjenta

No item in Technical Context was left as `NEEDS CLARIFICATION` — every open question was already
resolved either in `spec.md` (Clarifications, Assumptions) or by matching an existing pattern
already implemented in `patient-service`/`frontend` for the sibling sub-resource `tooth-chart`
(feature 002). This file records the architectural decisions made while translating the spec into
a design, in the same Decision/Rationale/Alternatives format the template expects.

## 1. Which service owns the new data — patient-service, not a new deployable

**Decision**: The three new tables (`allergy_entry`, `medication_entry`,
`chronic_condition_entry`) live in `patient-service`'s own Flyway history (next available:
`V3__medical_history.sql`), in a new package `com.dentalclinic.patient.medicalhistory`.

**Rationale**: This is a sub-resource of the existing patient record (Principle V — stays inside
the already-classified high-risk `patient records` module, no new failure domain, no new Helm
release/Deployment). Exactly the same reasoning `tooth_state` (V2) already established for a
per-patient clinical sub-resource.

**Alternatives considered**: A separate `medical-history-service` deployable — rejected, no
requirement in spec.md justifies a new failure domain/availability boundary, and it would violate
Principle V's "don't share... " read the other way — splitting a cohesive patient-record concern
across two deployables adds an availability *and* transactional-consistency boundary the spec
never asked for.

## 2. Audit event types — reuse the shared `audit_event_type` enum, generic + metadata discriminator

**Decision**: Add three new values to the existing, shared `audit_event_type` Postgres enum (owned
by `backend`'s migration history, next available: `V13__audit_event_type_medical_history.sql`,
mirroring how `V11` added 002's values): `MEDICAL_HISTORY_ENTRY_ADDED`,
`MEDICAL_HISTORY_ENTRY_VIEWED`, `MEDICAL_HISTORY_HISTORY_VIEWED` (the last one specifically for
opening the "historia zmian" — Clarifications Session 2026-08-29 Q2/Q3 — since that's a distinct,
separately auditable read of superseded/corrected data). Every write via `PatientAuditWriter`
(same single hash-chained `audit_log_entry` table, same advisory-lock writer `patient-service`
already uses for `tooth_state`) carries `metadataJson = {"entryType": "ALLERGY" | "MEDICATION" |
"CHRONIC_CONDITION"}` to disambiguate which of the three sections the event concerns.

**Rationale**: One event type per (entity × operation) would be 3 × 3 = 9 new enum values for no
behavioral difference in how they're written or interpreted — the existing `PatientAuditWriter.append`
signature already has a `metadataJson` parameter built for exactly this kind of discriminator, and
neither `TOOTH_STATE_CHANGED` nor any other existing event type encodes a sub-type this way only
because tooth-chart never needed one. Generic + metadata keeps the enum small and avoids a second,
parallel migration pattern.

**Alternatives considered**: Nine specific event types (`ALLERGY_ADDED`, `MEDICATION_ADDED`, …) —
rejected as unnecessary enum sprawl for data the `metadataJson` column already exists to carry.

## 3. Correction/lifecycle model — `record_status` column, not row mutation or a separate history table

**Decision**: Each of the three tables carries `record_status` (`CURRENT` / `SUPERSEDED`, a new
shared Postgres enum) and a nullable, self-referencing `supersedes_entry_id`. Adding a correction
= one `INSERT` of the new row (`record_status = CURRENT`, `supersedes_entry_id` = the id of the
entry it corrects) plus one `UPDATE` of the old row's `record_status` to `SUPERSEDED`, in the same
transaction. No row is ever deleted; no column value is ever overwritten after creation except
this one status flag.

**Rationale**: Directly implements FR-010 (Clarifications Session 2026-08-29 Q1: append-only,
`recordStatus` independent of any clinical-status field) with the smallest possible schema — one
column plus one nullable self-FK per table, versus standing up a fourth, separate
"history"/"audit" table per entity that would duplicate what `audit_log_entry` already captures via
before/after JSON on `MEDICAL_HISTORY_ENTRY_ADDED`. `supersedes_entry_id` is not strictly required
by any FR, but is near-zero-cost and gives the UI a direct way to render "this replaces X" instead
of inferring linkage from audit-log timestamps.

**Alternatives considered**: Mutating the existing row in place (rejected outright by FR-010,
Clarifications Q1) — and a dedicated `*_correction_log` table per entity (rejected: pure
duplication of the audit log's own before/after capability, per Principle III already in place).

## 4. Default-view / history split — two GET endpoints per entity type, not a query flag on one

**Decision**: `GET /patients/{patientId}/allergies` returns only `record_status = CURRENT` rows.
A second endpoint, `GET /patients/{patientId}/allergies/history`, returns the full set (current +
superseded), ordered so a superseded entry appears adjacent to whatever superseded it. Same split
for `/medications` and `/chronic-conditions`.

**Rationale**: Clarifications Session 2026-08-29 Q2 requires the default view to show only current
entries (SC-004 — no scrolling/extra interaction to see the critical-allergy signal) with history
behind an explicit "historia zmian" expansion. Two distinct, separately auditable
(`MEDICAL_HISTORY_ENTRY_VIEWED` vs. `MEDICAL_HISTORY_HISTORY_VIEWED`) endpoints make that an
explicit user action (matches Q3: ASSISTANT gets identical access to both) rather than an
easy-to-miss query parameter default.

**Alternatives considered**: Single endpoint with `?includeSuperseded=true` — rejected, mainly
because it would blur the two-distinct-audit-events decision above (harder to tell, from the audit
log alone, whether a "history open" happened) for no real reduction in surface area (still two
code paths either way).

## 5. RECEPTION's "fact of a critical alert, no detail" — a boolean on the existing patient-detail response

**Decision**: `PatientDetail` (the existing `GET /patients/{patientId}` response, already readable
by RECEPTION per `rbac-policy.md`) gains one new field: `hasCriticalAllergyAlert: boolean`,
computed server-side (`EXISTS` query against `allergy_entry` for `record_status = CURRENT AND
severity = CRITICAL`). No new endpoint, no new RBAC rule.

**Rationale**: FR-005 / Clarifications precedent (the original AskUserQuestion answer that shaped
this feature) requires RECEPTION to see the *fact* of a critical alert without any clinical detail
substance, reaction type, medication, or diagnosis). A boolean carries zero clinical content by
construction, so it's safe to add to a response RECEPTION already receives, instead of building a
new "alert summary" endpoint with its own RBAC rule to get equivalently reviewed.

**Alternatives considered**: A dedicated `/patients/{patientId}/critical-alert` endpoint restricted
to RECEPTION+DOCTOR+ASSISTANT — rejected as an unjustified new surface (and a new RBAC row to
review) for one boolean that an existing, already-reviewed endpoint can carry for free.

## 6. RODO export/erasure — extend export, no change to erasure

**Decision**: `PatientExportService` (`FR-009`) is extended to include all three entities'
**full** history (current + superseded — a subject-access request covers everything held, not just
the current view). `PatientErasureService` needs no change: it already only records an audited
*request* (`PATIENT_DATA_ERASURE_REQUESTED`) and defers actual deletion/anonymization execution to
a future feature (see its own class-level Javadoc, `TODO(T060)` — Polish medical-record retention
periods are statutorily defined per record type, so no single "done" deletion point exists yet).
That deferral already covers the new tables by construction; it isn't reopened here.

**Rationale**: Consistent with the existing, already-reviewed scope boundary for 002's erasure
flow — extending it here would be relitigating a decision this feature doesn't need to touch.

## 7. Frontend structure — one new tab, one new component, one new service

**Decision**: One new Angular component, `features/patients/medical-history/medical-history.component.ts`,
plugged into `patient-detail.component.ts` as a fourth `mat-tab` ("Historia medyczna"), gated by a
`canViewMedicalHistory` computed signal (`DOCTOR` or `ASSISTANT` — same shape as the existing
`canViewToothChart` guard, UX-only mirror of the backend's `@PreAuthorize`). The component renders
all three sections (alergie, leki, choroby) together on one screen (SC-001: no separate views), each
with a current-entries list, an expandable "Historia zmian" panel, and (DOCTOR only) an add-entry
form. A new `medical-history.service.ts` holds the six new HTTP calls (three resources × current +
history), following the same thin-relay pattern `PatientsService` already uses — kept as a separate
file rather than added to `patients.service.ts` to avoid that file growing to ~15 unrelated methods.
The `hasCriticalAllergyAlert` badge (via the existing `app-status-indicator`, `type="error"`) is
rendered in the patient-detail header itself, outside any tab, since RECEPTION must see it without
opening a tab it has no access to.

**Rationale**: Directly mirrors the established `tooth-chart` tab pattern (own component, own
service methods, role-gated tab visibility) — no new architectural shape introduced.

## 8. Cross-feature contract files — amended in place, not duplicated

**Decision**: `specs/001-staff-auth-rbac/contracts/rbac-policy.md` and
`specs/002-patient-records/contracts/patient-api.yaml` are amended directly by this feature (new
rows/paths marked `*Added by feature 004*`), the same way feature 002 amended both files that
originally belonged to 001. `specs/004-patient-medical-history/contracts/` holds a short pointer
file rather than a duplicate/competing API document.

**Rationale**: Established, working project convention (rbac-policy.md already carries "*Added by
feature 002*" rows; patient-api.yaml is already the one canonical API contract for
`patient-service`). Forking a second RBAC matrix or a second API contract file per feature would
make the two documents contradict each other over time.

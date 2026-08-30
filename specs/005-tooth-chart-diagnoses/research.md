# Phase 0 Research: Interaktywny odontogram z rozpoznaniami i powierzchniami zębów

Thirteen architectural decisions this plan is built on. Each reuses an existing mechanism from
`001`/`002`/`003`/`004` unless a genuinely new concern (diagnosis catalog, root canals, multi-select
bulk save) forces something new — per the user's explicit plan-time instruction, the mockup
(`mockup/odontogram-mockup.html`) is treated as a visual reference to follow closely wherever it
doesn't conflict with the existing token system, not as a contract.

## D1 — Replace `ToothState`/`ToothStatus`, do not migrate

**Decision**: A new migration `patient-service` `V4__tooth_chart_diagnoses.sql` drops
`tooth_state` and `tooth_status` (Postgres enum) entirely and creates the new schema (D2-D5) from
scratch. No migration/backfill code is written.

**Rationale**: spec.md Assumptions and Clarifications (session 2, Q1) are explicit: the system
isn't deployed to production and holds no patient data, so the binary model is *replaced*, not
migrated. Writing migration code for data that doesn't exist would be speculative work the
constitution's "no hypothetical requirements" norm (CLAUDE.md) argues against.

**Alternatives considered**: keep `tooth_state`/`tooth_status` alongside the new tables for
backward compatibility — rejected, no caller needs it (frontend's `tooth-chart.component.ts` is
being replaced in the same feature, D11) and it would leave dead code.

## D2 — `ToothChart` aggregate row; all 52 positions always exist, dentition mode is a view filter

**Decision**: A new `tooth_chart` table (one row per `patient_record`) holds `dentition_mode`
(`PERMANENT` / `DECIDUOUS` / `MIXED`). `ToothChartInitializer` creates all **52** `tooth_position`
rows (32 permanent FDI 11-48 + 20 deciduous FDI 51-85) for every patient at record-creation time,
regardless of `dentition_mode`. The mode only controls which positions the frontend renders/lists —
it never determines which rows exist.

**Rationale**: FR-047 requires that changing dentition mode "MUST NOT usuwać ani modyfikować
żadnego istniejącego wpisu"; the only way a stored finding can never be orphaned by a later mode
switch is if its parent position always existed. SC-011 explicitly measures "pełny tryb mieszany
(52 pozycje)" — confirming 52 is the full row count the chart must be able to hold at once, not an
upper bound reached by lazy creation.

**Alternatives considered**: lazily create deciduous positions only when a pediatric patient's
chart is first opened — rejected, adds a first-open write path with no benefit (52 small rows per
patient is negligible storage) and complicates FR-044's default-mode-from-age logic with a
"positions don't exist yet" edge case.

## D3 — `ToothFinding` reuses 004's two-independent-statuses pattern verbatim; "close" is a correction

**Decision**: `ToothFinding` carries the exact same two fields 004's `AllergyEntry`/
`ChronicConditionEntry` established: `record_status` (`CURRENT`/`SUPERSEDED`, technical correction
lifecycle) and `clinical_status` (`ACTIVE`/`RESOLVED`, the patient's actual state) — see
`data-model.md`. Both "korekta" (FR-033, fixing a mistake) and "zamknięcie" (FR-032, marking a
diagnosis resolved) are implemented as the *same* primitive: insert a new `CURRENT` row with
`supersedes_finding_id` pointing at the old one, flip the old row to `SUPERSEDED`. Closing simply
supersedes with every field copied forward except `clinical_status = RESOLVED` and
`resolved_date` set; a "real" correction supersedes with the corrected field(s) changed.

**Rationale**: 004's `ChronicConditionEntry` already proved this exact shape (an independent
clinical-status flip riding on a correction row) works for a materially identical problem —
reusing it means `ToothFindingService` can mirror `MedicalHistoryService.addAllergy(...)` almost
line-for-line (single supersede-then-insert transaction, one audit event per call), and 005's own
spec explicitly cites 004's pattern as the intended precedent (spec.md, "Kontekst i relacja...",
Assumptions).

**Alternatives considered**: a dedicated `closeFinding()` endpoint that mutates `clinical_status`
in place on the existing row — rejected, violates FR-030's append-only guarantee (the closed-vs-not
distinction is exactly the kind of clinical fact that must never be silently overwritten) and would
require a second, different concurrency/audit code path alongside the correction one.

## D4 — `RootCanal` is a small mutable entity, not an append-only clinical entry

**Decision**: `RootCanal` rows are mutated in place (name, `state`) with a before/after audit
snapshot on every change — the same idiom the *old* `ToothChartService.setStatus(...)` already used
for the binary tooth state it's replacing. Removing a canal sets `removed = true` (soft delete);
rows are never hard-deleted.

**Rationale**: FR-069 requires canal add/remove/state-change to be audit-logged with before/after
state, but does not require the append-only *correction* machinery (D3) — canals are administrative
facts about anatomy discovered during treatment, not clinical observations that could later be
disputed or need a documented correction trail. Soft-delete (not hard-delete) is what FR-068
actually requires: findings referencing a removed canal must keep referencing it and be flagged,
which is only possible if the row still exists.

**Alternatives considered**: model canals with the same `record_status`/supersede pattern as
`ToothFinding` — rejected as needless ceremony for a fact with exactly one mutable field (`state`)
and no dispute/correction semantics in the spec; would also complicate FR-067's "canal must
currently exist to be referenced" check with a superseded-row edge case.

## D5 — Diagnosis catalog is Flyway-seeded reference data, immutable via the app

**Decision**: `diagnosis_catalog_entry` is populated by the same `V4__tooth_chart_diagnoses.sql`
migration (`INSERT` statements, not application code) covering the FR-015 list. It exposes only
`GET` endpoints. The "inne rozpoznanie" fallback (FR-011a) is a normal seeded row with a
`requires_free_text = true` flag rather than special-cased application logic.

**Rationale**: FR-011 is explicit that the catalog "MUST NOT być edytowalny przez użytkowników
aplikacji, w tym ADMINISTRATORA" and that any change goes through the same pipeline as any other
code change (Principle VI) — a Flyway migration *is* that pipeline (reviewed, versioned, deployed
via the existing GitHub Actions workflow), whereas an admin CRUD screen would be a new, explicitly
out-of-scope surface (spec.md Assumptions).

**Alternatives considered**: load the catalog from a JSON/YAML resource file read at startup
instead of SQL rows — rejected, it would need its own versioning/audit story duplicate to what
Flyway already gives migrations for free, and would make "which catalog version produced this old
finding" (FR-019) harder to answer than a plain foreign key to a row that migrations only ever add
to, never edit or delete.

## D6 — Bulk multi-tooth/multi-part save is N independent creations in one call, no new entity

**Decision**: `POST /patients/{patientId}/tooth-chart/findings/bulk` (contracts) accepts a target
set (FDI numbers + optional surfaces/canal) and one diagnosis payload, and performs one
`ToothFindingService.addFinding(...)` call per applicable position inside a single transaction. The
response lists `created` (one `ToothFinding` per position) and `skipped` (position + reason, per
FR-004a/FR-006 scenario 3-4). No "bulk operation" or "batch" entity is persisted.

**Rationale**: spec.md Assumptions is explicit that multi-select is "narzędzie wprowadzania danych,
nie osobnym bytem — jego rezultatem jest zawsze zbiór niezależnych wpisów". Each resulting finding
needs its own independent history/correction lineage per US6 ("każdy da się później skorygować
niezależnie") — which is exactly what N independent rows already give for free, with zero new
concurrency or audit machinery beyond what D3/D9 already provide.

**Alternatives considered**: a `ToothFindingGroup` linking table so a bulk save's members can be
identified/undone together — rejected, no FR or acceptance scenario asks to undo a bulk save as a
unit (US6 scenario 2 explicitly asks for independent correction per tooth); would add a concept the
spec never needed.

## D7 — Optimistic concurrency via JPA `@Version` + a DB uniqueness constraint, no new framework

**Decision**: `ToothPosition` and `RootCanal` (the two mutable-in-place entities, D4) get a JPA
`@Version` column; a stale `PATCH` throws `ObjectOptimisticLockingFailureException`, mapped to
`409 Conflict` by `GlobalExceptionHandler`. For `ToothFinding` corrections (D3, append-only), a
partial unique index `UNIQUE (supersedes_finding_id) WHERE supersedes_finding_id IS NOT NULL`
ensures at most one correction can ever be inserted per superseded finding; a second concurrent
correction attempt hits a DB constraint violation, also mapped to `409`.

**Rationale**: FR-070/SC-010 require detecting a stale write and never silently overwriting another
user's change. Spring Data JPA's built-in optimistic locking is the smallest addition that
satisfies this for the two mutable entities; for the append-only entity, a uniqueness constraint at
the database level is strictly stronger than an application-level check-then-insert (no race window
at all) and needs no new column.

**Alternatives considered**: an application-level "read current version, compare before write"
check in the service layer for every entity — rejected in favor of `@Version` because it's the
framework-native mechanism already available (no new code to test for the common case) and because
a service-layer check-then-insert for the append-only case would have exactly the TOCTOU race the
DB constraint eliminates for free.

## D8 — RBAC: same `@PreAuthorize` + deny-404 shape, more rows, no new mechanism

**Decision**: Every new controller method uses the identical
`@PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")` the existing `ToothChartController` already
uses; `PatientNotFoundException`/`AccessDeniedException` continue to both map to `404` via the
existing `GlobalExceptionHandler`. `rbac-policy.md` gets new permission-matrix rows and a new
enforcement rule (rule 8) documenting that ASSISTANT's write scope is identical to DOCTOR's here —
a deliberate, spec-driven divergence from 004 (where ASSISTANT is read-only), not an inconsistency
to "fix".

**Rationale**: FR-057/FR-058/FR-059 ask for exactly the permission shape the existing tooth-chart
endpoints already enforce (DOCTOR + ASSISTANT full read/write, RECEPTION denied, ADMINISTRATOR
excluded by omission per rbac-policy.md rule 3/6) — there is no new authorization concept to
invent, only more endpoints to classify into the existing table. Because this *is* a deliberate
difference from a sibling feature, the plan calls it out explicitly (as spec.md FR-058 itself asks)
so the PR's required security/compliance review (constitution, Development Workflow & Quality
Gates) addresses it head-on rather than treating it as an oversight.

**Alternatives considered**: none seriously — inventing a different RBAC mechanism for this feature
alone would fragment the single enforcement point rbac-policy.md exists to be.

## D9 — Audit: reuse the shared hash-chained table; six new event types, one migration

**Decision**: All new writes/reads go through the existing `PatientAuditWriter` → shared
`audit_log_entry` table (owned by `backend`), exactly like `ToothChartService`/
`MedicalHistoryService` already do. `backend` gets a new migration
`V14__audit_event_type_tooth_chart_diagnoses.sql` adding six `audit_event_type` values:
`TOOTH_POSITION_PRESENCE_CHANGED`, `DENTITION_MODE_CHANGED`, `ROOT_CANAL_ADDED`,
`ROOT_CANAL_CHANGED`, `ROOT_CANAL_REMOVED`, `TOOTH_FINDING_ADDED` (the last one covers create,
correct, *and* close — all three are the same `supersede`-then-insert operation, D3). The existing
`TOOTH_CHART_VIEWED` value is reused as-is for the new, richer chart-read shape.
`TOOTH_STATE_CHANGED` is left in the Postgres enum, simply unused going forward — Postgres does not
support cheaply dropping a value from a native `enum` type once created, and since it costs nothing
to leave an inert label, no migration attempts removal.

**Rationale**: Principle III requires every read/write on clinical data to hit the append-only,
tamper-evident log; there is exactly one such log in this system by design (CLAUDE.md/002's
research.md #7) and inventing a second one for this feature would violate that design directly.

**Alternatives considered**: a `TOOTH_FINDING_CLOSED`/`TOOTH_FINDING_CORRECTED` split instead of
one `TOOTH_FINDING_ADDED` — rejected for the same reason 004 uses one `MEDICAL_HISTORY_ENTRY_ADDED`
for all its entry types: the audit row's `before_state`/`after_state` JSON already distinguishes
"first entry" (`before_state = null`) from "correction" (`before_state` = the superseded snapshot)
without a second event type.

## D10 — Seven new theme tokens, same enforcement points, no third canal-state color

**Decision**: Seven new token roles are added to `_pu-tokens.scss` (design source),
`brand-tokens.ts` (TS mirror), `_pu-theme.scss` (`--mat-sys-*`/`light-dark()` runtime), and
`contrast-pairs.ts` (WCAG 1.4.11 non-text pairs, 3.0:1 minimum, matching the three existing
tooth-token pairs) — named after the mockup's invented CSS variables: `tooth-root-fill`,
`tooth-restored-fill`, `tooth-restored-stroke`, `tooth-closed-stroke`, `tooth-absent`,
`canal-treat`, `canal-done`. The mockup's third canal state ("niedoleczony", FR-066) is rendered by
combining `canal-treat` + `canal-done` on one shape (green body, red apex), not a third dedicated
color token — kept exactly as the mockup does it, since it satisfies FR-066a's "additional
non-color signal" requirement (the apex/body split is itself the non-color cue) without growing the
token surface.

**Rationale**: `token-parity.spec.ts` and `contrast-audit.spec.ts` are the two live CI checks
(`frontend-unit`) that gate every brand color (CLAUDE.md, Theming) — adding tokens through the same
four files these tests already read is the only way new odontogram colors are actually enforced in
CI; a token added anywhere else would not be checked. This directly answers the plan-time
instruction to flag mockup styling that lives outside the token system: these seven are exactly
that list.

**Alternatives considered**: reuse existing `tooth-diseased-*`/`tooth-healthy-*` tokens for the new
states (e.g. render "restored" in the diseased color) — rejected, FR-039/FR-050 require every
distinct state to be visually distinguishable, and collapsing "existing state" (a filling) onto the
same color as "active disease" would misrepresent exactly the layer distinction FR-009/FR-016
exist to make.

## D11 — Frontend: a small component tree replaces the single rect-based component; geometry ported as pure functions

**Decision**: `tooth-chart.component.ts` (currently ~120 lines, `<rect>`-per-tooth, single-select
only) is replaced by a container component plus: `tooth-arch.component.ts` (renders one arch),
`surface-map.component.ts` (used at both the middle-strip size and the enlarged detail-panel size),
`tooth-detail-panel.component.ts`, `tooth-context-menu.component.ts`, and a plain
`tooth-geometry.ts` utility module holding pure functions ported from the mockup's `crownPath()`,
`rootGeometry()`, `canalNodes()`, and `zoneDefs()` (parameterized by cusp count / root count per
tooth type, not per-tooth fixed artwork).

**Rationale**: this is the direct, load-bearing consequence of the plan-time instruction to follow
the mockup closely wherever it doesn't conflict with the token system — FR-001a's anatomically
recognizable, per-type tooth silhouettes are exactly what the mockup's procedural SVG generation
produces, and porting the same parameterized functions (rather than commissioning N static SVG
assets, one per tooth type/root-count combination) is both closer to the mockup's actual approach
and the smaller amount of new code, since it's already written and validated against every FR the
mockup was built to cover (spec.md, "Mockup UI" section).

**Alternatives considered**: fixed SVG `<path>` artwork per tooth type (8-10 static shapes) —
rejected, it would diverge from the mockup's actual rendering approach for no benefit, and would
need separate assets for every cusp/root-count combination the mockup instead derives from two
integer parameters.

## D12 — Quick-add menu: static "most common" flag + client-side "recently used", no personalization table

**Decision**: `DiagnosisCatalogEntry.quick_access` (boolean, seeded) drives the "najczęstsze
rozpoznania" section of FR-020a's context menu. "Ostatnio używane" is tracked entirely client-side —
`localStorage`, keyed by the signed-in clinician's account id, a capped recency list updated on
every successful save — not a new backend table.

**Rationale**: no FR or success criterion asks for "recently used" to sync across devices/sessions,
and a per-browser MRU cache satisfies FR-020a's chairside-speed goal (SC-001, SC-013) with zero new
schema, endpoints, or audit surface. This keeps the quick-add path itself audited exactly like any
other finding creation (D9) — the personalization layer is purely a client-side convenience on top
of the same `POST .../findings` call every other creation path uses.

**Alternatives considered**: a server-side `recent_diagnosis_use` table per clinician — rejected as
solving a cross-device-sync problem the spec never states, at the cost of a new table, new
endpoints, and a new thing to audit/RODO-classify (it would be tied to a staff account, not patient
data, but still adds surface with no requirement demanding it).

## D13 — Contracts: amend the two existing canonical files in place, same convention as 004

**Decision**: No new OpenAPI file. `specs/002-patient-records/contracts/patient-api.yaml` gets its
`ToothState` schema and `/tooth-chart` paths replaced by the new schemas/paths (D2-D7);
`specs/001-staff-auth-rbac/contracts/rbac-policy.md` gets new permission-matrix rows plus
enforcement rule 8 (D8). `specs/005-tooth-chart-diagnoses/contracts/README.md` is a pointer file
explaining exactly what changed where, mirroring `specs/004-patient-medical-history/contracts/
README.md`'s own shape.

**Rationale**: this is the established convention — 002 originally owned `patient-api.yaml` and
004 already amended it in place rather than forking a competing copy (004's own research.md #8);
005 replacing 002's own tooth-chart section of the same file is the same move one feature earlier
in the dependency chain. A second competing API-contract file would create ambiguity about which
one is authoritative.

**Alternatives considered**: a `005-tooth-chart-diagnoses/contracts/tooth-chart-api.yaml` forked
from the tooth-chart section of `patient-api.yaml` — rejected for the reason above; it would also
orphan the `PatientFullExport` schema's `toothChart` field across two files instead of one.

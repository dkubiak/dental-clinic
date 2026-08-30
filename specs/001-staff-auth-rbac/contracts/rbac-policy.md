# RBAC Policy Contract

This is the authoritative permission matrix that `@PreAuthorize`/service-layer checks in the
backend MUST implement (see plan.md — the backend is the sole enforcement point; the Angular UI
only reflects this policy for UX, per FR-005). Any new endpoint or resource added in a future
feature MUST be classified into this table before it ships (Development Workflow & Quality Gates —
changes touching authz require security/compliance review).

## Roles

| Role | Polish name | Least-privilege scope |
|---|---|---|
| `RECEPTION` | recepcja | Appointments and patient contact details, for all patients. No clinical/medical data. |
| `DOCTOR` | lekarz | Medical records and treatment history, for all patients (clinic-wide, not per-doctor assignment — FR-014). No account/config administration. |
| `ADMINISTRATOR` | administrator | User accounts and system configuration. No default access to clinical patient data (contact, medical, or otherwise). |
| `ASSISTANT` | asystent/asystentka | *Added by feature 002 (002-patient-records).* Chairside assistant. Read-only patient basic data (identification only) + full read/write parity with `DOCTOR` on the odontogram, including diagnosis findings (rule 8, feature 005) — extended from the original tooth-chart-only scope. No account/config administration, no basic-data write, no audit/export/erasure access. |

## Permission matrix

| Resource / action | RECEPTION | DOCTOR | ASSISTANT | ADMINISTRATOR |
|---|---|---|---|---|
| View/manage appointments | ✅ | ❌ | ❌ | ❌ |
| View/manage patient contact details | ✅ | ❌ | ❌ | ❌ |
| View/manage medical records & treatment history (any patient) | ❌ | ✅ | ❌ | ❌ |
| Create / deactivate / reactivate staff accounts | ❌ | ❌ | ❌ | ✅ |
| Assign / change staff roles | ❌ | ❌ | ❌ | ✅ |
| Reset another account's MFA enrollment (FR-015b) | ❌ | ❌ | ❌ | ✅ |
| System configuration | ❌ | ❌ | ❌ | ✅ |
| Read audit log | ❌ | ❌ | ❌ | ✅ |
| Write/edit/delete audit log (any role, any endpoint) | ❌ | ❌ | ❌ | ❌ (no such capability exists — FR-008) |
| *Added by feature 002 (002-patient-records):* | | | | |
| Create / edit patient basic data (kartoteka) | ✅ | ✅ | ❌ | ❌ |
| Read patient basic data (identification only) | ✅ | ✅ | ✅ | ❌ |
| View visit-history placeholder | ✅ | ✅ | ❌ | ❌ |
| Export / erase patient data (RODO, FR-009/FR-010 of 002) | ❌ | ✅ | ❌ | ❌ |
| *Added by feature 004 (004-patient-medical-history):* | | | | |
| Add allergy / medication / chronic-condition entries (kartoteka historii medycznej) | ❌ | ✅ | ❌ | ❌ |
| Read current + "historia zmian" entries for allergies / medications / chronic conditions | ❌ | ✅ | ✅ | ❌ |
| See fact-only critical-allergy alert (boolean, no clinical detail — `hasCriticalAllergyAlert` on the existing patient-basic-data response) | ✅ | ✅ | ✅ | ❌ |
| *Replaced by feature 005 (005-tooth-chart-diagnoses) — supersedes the "View / edit tooth chart" row above:* | | | | |
| Read odontogram (chart, positions, canals, current findings, per-position history) | ❌ | ✅ | ✅ | ❌ |
| Add / correct / close diagnosis findings, including disease diagnoses (rozpoznania chorobowe) | ❌ | ✅ | ✅ | ❌ |
| Add / rename / change state / remove root canals | ❌ | ✅ | ✅ | ❌ |
| Set tooth presence (obecny/usunięty/wrodzony brak/niewyrznięty) and dentition mode | ❌ | ✅ | ✅ | ❌ |
| Read the diagnosis catalog (search, quick-access list) | ❌ | ✅ | ✅ | ❌ |

## Enforcement rules

1. **Server is the source of truth.** Every request is authorized server-side
   (`@PreAuthorize`/service-layer check against this table), regardless of how the request arrives
   (UI navigation, direct URL, API call) — satisfies FR-005.
2. **Deny → `404`, never `403`.** A role-scope denial returns `404 Not Found`, identical to a
   genuinely nonexistent resource, so the response never confirms the resource exists
   (research.md #8). `401 Unauthorized` is reserved for "no valid session at all."
3. **No implicit admin clinical access.** `ADMINISTRATOR` has zero rows granting patient-data
   access in the table above — this is intentional, not an oversight, and any future change
   granting administrators clinical-data access requires an explicit spec/constitution-level
   decision, not a quiet code change (matches spec.md Acceptance Scenario US1-3 and US3-3).
4. **Role is the only boundary for `DOCTOR`.** There is no per-doctor patient assignment table;
   every `DOCTOR` account has identical access to all patients' medical records (FR-014,
   Clarifications session 2026-08-16). Do not reintroduce a doctor–patient assignment check
   without a spec change — the clarification session explicitly removed that concept.
5. **Every denial and every permission change is audited**, per FR-006/FR-007 and the
   AuditLogEntry `event_type` enum in data-model.md (`ACCESS_DENIED_OUT_OF_ROLE`, `ROLE_CHANGED`,
   etc.).
6. **`ADMINISTRATOR` clinical-data exclusion extends to patient records (feature 002).** Rule 3
   above applies unchanged to the rows feature 002 added: `ADMINISTRATOR` has zero rows granting
   patient basic-data, tooth-chart, or export/erasure access. `DOCTOR` — not `ADMINISTRATOR` —
   owns RODO export/erasure (002's research.md #6), specifically to avoid a quiet violation of
   rule 3.
7. **`ADMINISTRATOR` exclusion extends to medical history (feature 004); `ASSISTANT` read parity
   is deliberate, not an oversight.** `ADMINISTRATOR` again has zero rows for allergy/medication/
   chronic-condition data (rule 3/6, unchanged rationale). `ASSISTANT`'s read access explicitly
   covers *both* the current view and the "historia zmian" (superseded/corrected entries) view,
   identical in scope to `DOCTOR`'s own read access — 004's Clarifications session confirmed this
   directly rather than defaulting to a narrower scope (004's research.md #4). `RECEPTION` gets no
   row here at all; its only visibility into this data is the pre-existing, unrelated
   `hasCriticalAllergyAlert` boolean carried on the basic-data response it already reads — that
   boolean reveals no substance/reaction/medication/diagnosis content, so it does not create a new
   permission row of its own (004's research.md #5).
8. **`ASSISTANT` write parity with `DOCTOR` on the odontogram (feature 005) is deliberate, not a
   copy-paste error, and not the same shape as rule 7.** Unlike medical history (004), where
   `ASSISTANT` is read-only, `ASSISTANT` here has the *exact same* create/correct/close/canal/
   presence write scope as `DOCTOR` (005's spec.md FR-057/FR-058, Clarifications session
   2026-08-30) — reflecting the real chairside workflow of a doctor dictating a diagnosis and an
   assistant recording it. Accountability for this parity does not come from a narrower role; it
   comes from `authorRole` being recorded on every finding (which role the author acted in at
   write time) plus the unconditional audit trail (rule 5) — per 005's research.md D8, this
   divergence from rule 7's read-parity/write-restricted shape MUST be called out explicitly in
   any future change to this table, never silently generalized into "ASSISTANT always has DOCTOR's
   write access." `ADMINISTRATOR` exclusion is unchanged (rule 3/6/7); `RECEPTION` has zero rows
   for the odontogram, including no fact-only alert equivalent to rule 7's
   `hasCriticalAllergyAlert` — 005's spec.md FR-059 requires denying RECEPTION knowledge that any
   finding exists at all, not just its clinical detail.

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

## Permission matrix

| Resource / action | RECEPTION | DOCTOR | ADMINISTRATOR |
|---|---|---|---|
| View/manage appointments | ✅ | ❌ | ❌ |
| View/manage patient contact details | ✅ | ❌ | ❌ |
| View/manage medical records & treatment history (any patient) | ❌ | ✅ | ❌ |
| Create / deactivate / reactivate staff accounts | ❌ | ❌ | ✅ |
| Assign / change staff roles | ❌ | ❌ | ✅ |
| Reset another account's MFA enrollment (FR-015b) | ❌ | ❌ | ✅ |
| System configuration | ❌ | ❌ | ✅ |
| Read audit log | ❌ | ❌ | ✅ |
| Write/edit/delete audit log (any role, any endpoint) | ❌ | ❌ | ❌ (no such capability exists — FR-008) |

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

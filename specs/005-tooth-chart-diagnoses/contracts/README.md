# Contracts for 005-tooth-chart-diagnoses

Per research.md D13, this feature amends the two existing, canonical cross-feature contract files
in place rather than forking a competing copy — the same convention feature 004 already used when
it added rows to files 001/002 originally owned:

- **[`specs/001-staff-auth-rbac/contracts/rbac-policy.md`](../../001-staff-auth-rbac/contracts/rbac-policy.md)**
  — the existing "View / edit tooth chart" row (added by 002) is replaced by a more specific set of
  rows under "*Added by feature 005*" (odontogram read, findings write, root-canal write, diagnosis
  catalog read), and a new rule 8 (Enforcement rules) documents that ASSISTANT's write scope here is
  full parity with DOCTOR — a deliberate divergence from 004's ASSISTANT-read-only scope, not an
  inconsistency.
- **[`specs/002-patient-records/contracts/patient-api.yaml`](../../002-patient-records/contracts/patient-api.yaml)**
  — the `ToothState` schema and the two `/tooth-chart` paths (originally added by 002) are replaced
  by the new schemas (`ToothChart`, `ToothPosition`, `RootCanal`, `DiagnosisCatalogEntry`,
  `ToothFinding`, and their `*Request`/`*Response` counterparts) and a wider set of paths covering
  presence, dentition mode, canals, findings (single + bulk + close + correct), and the read-only
  diagnosis catalog. `PatientFullExport.toothChart`'s shape changes accordingly; the field itself
  keeps its name and position in the schema.

See `../data-model.md` for the underlying entities and `../research.md` (D1-D13) for the reasoning
behind the endpoint split, the RBAC divergence, and the two amended-in-place files.

# Contracts for 004-patient-medical-history

Per research.md #8, this feature amends the two existing, canonical cross-feature contract files
in place rather than forking a competing copy — the same convention feature 002 already used when
it added rows to a document 001 originally owned:

- **[`specs/001-staff-auth-rbac/contracts/rbac-policy.md`](../../001-staff-auth-rbac/contracts/rbac-policy.md)**
  — new rows under "*Added by feature 004*" (permission matrix) and new rule 7 (Enforcement
  rules): DOCTOR full read/write, ASSISTANT full read (current + "historia zmian") parity with
  DOCTOR, RECEPTION restricted to the fact-only `hasCriticalAllergyAlert` boolean it already
  receives via basic-data reads, ADMINISTRATOR excluded entirely (consistent with existing rule
  3/6).
- **[`specs/002-patient-records/contracts/patient-api.yaml`](../../002-patient-records/contracts/patient-api.yaml)**
  — `hasCriticalAllergyAlert` added to `PatientDetail`; six new paths
  (`/patients/{patientId}/{allergies,medications,chronic-conditions}` and their `/history`
  siblings); `PatientFullExport` extended with `allergies`/`medications`/`chronicConditions`; new
  schemas `AllergyEntry`, `MedicationEntry`, `ChronicConditionEntry`, `RecordStatus`, and their
  `*CreateRequest` counterparts.

See `../data-model.md` for the underlying entities and `../research.md` (#3, #4, #5, #6, #8) for
the reasoning behind the endpoint split and the two amended-in-place files.

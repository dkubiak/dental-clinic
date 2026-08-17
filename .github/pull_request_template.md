## Summary

<!-- What does this PR change and why? -->

## Test plan

<!-- How was this verified? CI must be green before merge (Principle I / Development Workflow & Quality Gates). -->

## Security / compliance self-review

Does this PR touch **patient data, authentication, authorization, or audit logging**?

- [ ] No — none of the above. This PR merges on green CI alone (constitution v1.5.0,
      Development Workflow & Quality Gates).
- [ ] Yes — the checklist below MUST be completed and this box checked before merge.
      Auto-merge MUST NOT be used for this PR.

If yes, confirm each item (or mark N/A with a one-line reason):

- [ ] **RBAC scope**: access to any new/changed endpoint or UI is restricted to the
      correct job function(s) (recepcja / lekarz / administrator) on a least-privilege
      basis (Principle II).
- [ ] **Encryption**: any patient data added or touched is encrypted at rest and in
      transit (Principle II).
- [ ] **Audit logging**: every create/read/update/delete on patient or clinical data
      introduced by this change is captured in the append-only audit log with
      who/what/when/before-after state, and remains non-editable/non-deletable through
      normal app flows (Principle III).
- [ ] **RODO/GDPR subject rights**: export, erasure, and retention behavior for any
      affected data is unchanged or still correct (Principle II).

Reviewer: sole contributor, self-attested per constitution v1.5.0 Development Workflow
& Quality Gates (risk-tiered review, solo-developer phase). `TODO(SECOND_CONTRIBUTOR)`
in the constitution: revisit once a second contributor joins.

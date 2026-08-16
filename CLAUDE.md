# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Working mode: delegate to Spec Kit

This project is developed exclusively via Spec-Driven Development (SDD) using the
[GitHub Spec Kit](https://github.com/github/spec-kit) (`speckit`) framework. Do not improvise an
ad-hoc process — every feature-level request (new feature, change of scope, new requirement) must
be routed through the `/speckit-*` slash command pipeline described below, in order, rather than
jumping straight to editing code or writing specs/plans by hand. If the user asks for something
that skips a step (e.g. "just implement X"), point out which `/speckit-*` step is missing and
either run it first or get explicit confirmation to skip it. `.specify/` is the framework's own
directory (scripts, templates, memory) and generally shouldn't be edited by hand — let the
`/speckit-*` commands manage it.

## Project state

This repository currently contains only the [Spec Kit](https://github.com/github/spec-kit) (`speckit`)
scaffolding — no application source code has been written yet (no frontend, backend, infra, or
CI/CD config exists). Work here proceeds through Spec-Driven Development (SDD): specs and plans are
authored via `/speckit-*` slash commands before any implementation code is written. Do not assume
directories like `frontend/`, `backend/`, `infra/`, or `.github/workflows/` exist — check first.

## Spec-Driven Development workflow

Features are developed through an ordered sequence of slash commands, each consuming the previous
step's output. Run them in this order for a new feature:

1. `/speckit-constitution` — create/amend `.specify/memory/constitution.md` (the project's binding
   principles; supersedes all other conventions).
2. `/speckit-specify` — write `spec.md` for a feature from a natural-language description.
3. `/speckit-clarify` — ask up to 5 targeted questions to resolve ambiguity in `spec.md` (run before
   `/speckit-plan` unless the user explicitly skips it).
4. `/speckit-plan` — generate design artifacts (`plan.md`, `research.md`, `data-model.md`,
   `quickstart.md`, `contracts/`) from the spec.
5. `/speckit-tasks` — generate a dependency-ordered `tasks.md` from the plan artifacts.
6. `/speckit-analyze` — non-destructive cross-check of `spec.md` / `plan.md` / `tasks.md` for
   consistency gaps (read-only; run after `/speckit-tasks`, before `/speckit-implement`).
7. `/speckit-checklist` — optionally generate a requirements-quality checklist ("unit tests for
   English", not test cases) for a specific domain/focus area.
8. `/speckit-implement` — execute `tasks.md` and write the actual implementation.
9. `/speckit-converge` — after implementation, diff the codebase against spec/plan/tasks and append
   any unbuilt work back onto `tasks.md`.
10. `/speckit-taskstoissues` — optionally convert `tasks.md` into GitHub issues instead of
    implementing directly.

Each feature's artifacts live under a feature directory resolved via `.specify/feature.json`
(`FEATURE_DIR`, containing `spec.md`, `plan.md`, `tasks.md`, etc.) — see
`.specify/scripts/bash/common.sh:get_feature_paths` for the resolution logic (env var override →
`feature.json` → error). Helper scripts (`create-new-feature.sh`, `setup-plan.sh`,
`setup-tasks.sh`, `check-prerequisites.sh`) back the slash commands and generally shouldn't be
invoked by hand.

Template precedence when a command resolves a template (spec/plan/tasks/etc.): project overrides
in `.specify/templates/overrides/` → installed presets in `.specify/presets/` → extensions in
`.specify/extensions/` → core templates in `.specify/templates/`.

## Constitution — binding project rules

`.specify/memory/constitution.md` is the authoritative source of truth (currently v1.3.1) and
supersedes any other convention. Every `/speckit-*` output and every PR must be checked against it.
Key points future work must honor:

**Technology stack (binding — deviation requires a constitution amendment, not a plan-level
decision):**
- Frontend: Angular (current LTS), mobile-first (Principle IV) — design for phone/tablet first,
  then progressively enhance for desktop.
- Backend: Java.
- Database: PostgreSQL, hosted only on AWS-managed RDS/Aurora (never self-hosted).
- Hosting: AWS only, on Amazon EKS (no self-managed Kubernetes, no other cloud).
- IaC: Terraform for all AWS resources.
- Kubernetes delivery: Helm + Argo CD (GitOps, pull-based). No Ansible or other imperative
  config tools for cluster/app deployment.
- Progressive delivery: Argo Rollouts + AWS Load Balancer Controller for weighted canary traffic
  shifting (no service mesh unless a future amendment justifies it).
- CI/CD: GitHub Actions only, defined as workflow files under `.github/workflows/`. No manual/
  ClickOps steps anywhere in the delivery path (build, Terraform plan/apply, Helm/Argo CD sync all
  run as code-defined workflows).

**Non-negotiable principles:**
- **Test-First Development**: tests (backend Java and frontend Angular) must be written and shown
  to fail before implementation (Red-Green-Refactor). No merging implementation without a
  preceding failing test.
- **Patient Data Protection & RODO/GDPR compliance**: patient health data is GDPR Art. 9 special
  category data — encrypted at rest and in transit, RBAC scoped to job function (recepcja, lekarz,
  administrator) on least-privilege, and subject-rights (export/erasure/retention) satisfied before
  a feature touching patient data is "done".
- **Full Auditability**: every create/read/update/delete on patient or clinical data must go to an
  append-only, tamper-evident audit log (who/what/when/before-after state); audit logs are never
  editable or deletable through normal app flows, even by admins.

**Other principles:**
- **Mobile-First Design** (Principle IV, see above).
- **Risk-Tiered High Availability** (Principle V): modules are classified by risk tier; patient
  records, scheduling, and billing are high-risk and must not share a failure domain with lower-tier
  modules (e.g. reporting, admin config). Document module boundaries in the plan for any feature
  touching a high-risk module.
- **Infrastructure & Delivery as Code** (Principle VI, non-negotiable): every change — app code,
  infra, cluster config, or pipeline definitions — goes through the GitHub Actions pipeline defined
  in code; no supported path to production bypasses it. Emergency break-glass changes must be
  reconciled back into code within one business day.

**Environments & release process:**
- Only DEV (ephemeral, on-demand, own Terraform workspace/state, torn down after use) and PROD
  (persistent, always-on) — no persistent shared staging.
- All PROD deployments go through canary progressive delivery (weighted traffic stages with
  health/error-rate observation) before reaching 100%; deploying straight to 100% is forbidden
  except documented emergency rollback to a previously-canaried version.

**Workflow gates:**
- All changes (app, infra, deployment config, pipelines) via PR only — no direct commits to main.
- CI must run and pass the full test suite before merge; merge to main is what triggers deployment.
- Changes touching patient data, auth, authz, or audit logging require explicit security/compliance
  review before merge.
- Changes introducing/modifying a high-risk module must document their availability approach in the
  `/speckit-plan` output before `/speckit-implement` proceeds.
- At least one other contributor must review before merge. This and the security/compliance
  review above are conceptually distinct checks, but MAY be satisfied by the same reviewer's
  approval — a separate second person is not mandated solely to cover both.

When a `/speckit-plan` or implementation decision would conflict with any of the above, call out the
conflict explicitly rather than silently deviating — per Governance, amendments require documented
rationale, a semantic version bump, and review.

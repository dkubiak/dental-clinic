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

The application is real and partly built. Two features are implemented and a third is specified,
planned and broken into tasks but not yet started.

| Feature | State |
| --- | --- |
| `specs/001-staff-auth-rbac` | Implemented. Two tasks remain, both Terraform-only (DEV workspace apply/destroy, ALB TLS policy pin). |
| `specs/002-patient-records` | Implemented, all tasks done. |
| `specs/003-brand-ui-theme` | Implemented (Phases 1–8). One task remains blocked (T049: depends on a patient "allergy" field that doesn't exist in feature 002's data model — see `specs/003-brand-ui-theme/tasks.md` Notes). |

### Layout

| Path | What it is |
| --- | --- |
| `backend/` | **Auth service** — Java 25, Spring Boot 4.1, Gradle Kotlin DSL, package `com.dentalclinic.auth`. Despite the generic directory name, this is one service, not a monolith. |
| `patient-service/` | Patient records service — same stack, package `com.dentalclinic.patient`. |
| `frontend/` | Angular 21 + Angular Material 21 (Material 3), standalone components with inline `styles:`. Vitest for unit tests, Playwright for e2e. |
| `infra/terraform/` | All AWS resources. |
| `helm/` | Three charts: `frontend`, `auth-service`, `patient-service`. |
| `docker/postgres/` | Local Postgres for development. |
| `.github/workflows/` | `ci.yml`, `deploy.yml`, `terraform.yml`. |
| `design/brand/` | Approved colour system (`_pu-tokens.scss`) — feature 003 consumes it. |

**Naming trap**: the directory is `backend/`, but the Helm chart, the deploy workflow and the
Kubernetes service all call it `auth-service`. Both names refer to the same thing.

### Build and test

```bash
cd backend && ./gradlew build          # JUnit 5 + Testcontainers + checkstyle
cd patient-service && ./gradlew build  # same
cd frontend && npm ci && npm run lint && npm test   # Vitest
cd frontend && npm run e2e             # Playwright — needs a running backend
```

`scripts/quiet.sh <command>` wraps any of the above (Gradle's Testcontainers/checkstyle output and
Playwright's traces are the worst offenders for chewing through context in agent sessions). It
buffers the command's output: on success it prints one pass line plus the last `QUIET_TAIL_LINES`
lines (default 20); on failure it prints everything, unfiltered, so a real error is never hidden.
Run it as `../scripts/quiet.sh ./gradlew build` from `backend/` or `patient-service/`; the frontend
has it pre-wired as `npm run test:quiet`, `lint:quiet`, `e2e:quiet`. This is enforced, not just a
suggestion: a `PreToolUse` hook (`.claude/settings.json` → `.claude/hooks/enforce-quiet-build.mjs`)
blocks raw `gradlew build/test/check` and raw `npm test`/`run lint`/`run e2e` from an agent Bash
call, pointing at the quiet variant instead. A human running these by hand in a terminal is
unaffected — the hook only gates the Claude Code agent's own tool calls.

CI runs three jobs: `backend`, `patient-service`, `frontend-unit`. A fourth job, `frontend-e2e`,
is **disabled** (`if: false` in `ci.yml`) because it needs Postgres and LocalStack, which the job
does not yet provision. Anything that must actually be gated therefore has to be reachable from
one of the three live jobs — putting a check only in Playwright means it never runs in CI. A fifth
job, `frontend-e2e-theme` (added by feature 003), is live — see Theming below for why it's a
separate job from the disabled `frontend-e2e` rather than an unblocking of it.

### Theming

Feature `003-brand-ui-theme` replaced Angular Material's default purple with the Projekt Uśmiech
brand palette and added a light/dark toggle available on every screen, including the four
pre-auth ones (login, MFA challenge, both password-reset screens).

- **Switching**: click the toggle (`data-testid="theme-toggle"`, in the app shell and on the
  pre-auth screens). The only state carrier in the DOM is the CSS `color-scheme` property on
  `<html>` — no theme class, no `data-*` attribute (`ThemeService`,
  `frontend/src/app/core/theme/theme.service.ts`, FR-026). The choice persists to `localStorage`
  under `pu.theme`; absent that key, the app follows `prefers-color-scheme` and reacts live to it
  changing.
- **Source of truth for tokens**: `design/brand/_pu-tokens.scss` is the design proposal — role
  names, hex values, and the contrast rationale live in its comments.
  `frontend/src/styles/brand-tokens.ts` mirrors it as the machine-readable source the contrast
  audit consumes; `frontend/src/styles/token-parity.spec.ts` fails the build if the two drift.
  `frontend/src/styles/_pu-theme.scss` maps those roles onto Angular Material's `--mat-sys-*`
  variables — each value as `light-dark(#light, #dark)`, because a plain value would freeze the
  token against theme switching — and that file is what `frontend/src/styles.scss` actually feeds
  into `mat.theme()`.
- **Why the contrast audit lives in Vitest, not Playwright**: `frontend-e2e` (see above) is
  disabled in CI, so a check that only ran there would never actually gate anything.
  `frontend/src/styles/contrast-audit.spec.ts` computes WCAG 2.1 contrast ratios directly from
  `brand-tokens.ts` in plain TypeScript, so it runs under `frontend-unit`, which is live.
  Browser-rendered checks that need an actual layout (real contrast on rendered buttons, 320px
  width, no flash of the wrong theme on load) still need a browser; those live in
  `frontend/e2e/us2-theme-toggle.spec.ts` and `frontend/e2e/us5-theme-contrast.spec.ts`, gated by
  the separate `frontend-e2e-theme` job (build + static file server, no backend — the toggle works
  pre-auth, so the whole feature is verifiable without Postgres or LocalStack).

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
(`FEATURE_DIR`, containing `spec.md`, `plan.md`, `tasks.md`, etc.). **`feature.json` is
gitignored**, so a fresh clone has none and the `/speckit-*` commands cannot tell which feature is
active — write it before running them, e.g.
`echo '{"feature_directory": "specs/003-brand-ui-theme"}' > .specify/feature.json`. See
`.specify/scripts/bash/common.sh:get_feature_paths` for the resolution logic (env var override →
`feature.json` → error). Helper scripts (`create-new-feature.sh`, `setup-plan.sh`,
`setup-tasks.sh`, `check-prerequisites.sh`) back the slash commands and generally shouldn't be
invoked by hand.

Template precedence when a command resolves a template (spec/plan/tasks/etc.): project overrides
in `.specify/templates/overrides/` → installed presets in `.specify/presets/` → extensions in
`.specify/extensions/` → core templates in `.specify/templates/`.

## Constitution — binding project rules

`.specify/memory/constitution.md` is the authoritative source of truth (currently v1.5.0) and
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
  category data — encrypted at rest and in transit, RBAC scoped to job function on least-privilege,
  and subject-rights (export/erasure/retention) satisfied before a feature touching patient data is
  "done". The constitution names recepcja, lekarz and administrator as examples; feature 002 added
  a fourth role, so the implemented set is `RECEPTION | DOCTOR | ADMINISTRATOR | ASSISTANT`
  (`frontend/src/app/core/auth/auth-state.ts`).
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
- Code review is **risk-tiered** for the project's current solo-developer phase (v1.5.0 replaced
  the earlier blanket "one other contributor must review" rule, which was unworkable with a single
  contributor since GitHub forbids self-approval):
  - Changes **not** touching patient data, auth, authz or audit logging: a green CI run is enough
    to merge. Self-merge is permitted and auto-merge-on-green MAY be enabled.
  - Changes that **do** touch those areas: the security/compliance review above must still be
    documented in the PR before merge, self-attested by the sole contributor. Auto-merge MUST NOT
    be enabled on these paths — merge stays a deliberate manual action after that review.
  - `TODO(SECOND_CONTRIBUTOR)`: reinstate independent two-person review once a second contributor
    joins.

When a `/speckit-plan` or implementation decision would conflict with any of the above, call out the
conflict explicitly rather than silently deviating — per Governance, amendments require documented
rationale, a semantic version bump, and review.

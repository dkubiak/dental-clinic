<!--
Sync Impact Report
- Version change: 1.2.0 → 1.3.0
- Modified principles:
  - VI. Infrastructure as Code → VI. Infrastructure & Delivery as Code (expanded to
    cover CI/CD pipeline definitions, not just infra/cluster state)
- Added principles: none
- Modified sections:
  - Technology Stack Constraints: added binding CI/CD tooling (GitHub Actions,
    workflows stored in-repo under .github/workflows/)
  - Development Workflow & Quality Gates: every change (app, infra, deployment
    config) MUST run through the GitHub Actions pipeline; no manual/ClickOps steps
    anywhere in the delivery path
- Added sections: none
- Removed sections: none
- Deferred TODOs: none
-->

<!--
Constitution amendment history (older Sync Impact Reports, newest first):

1.1.0 → 1.2.0
- Added principles: VI. Infrastructure as Code
- Modified sections: Technology Stack Constraints (added Terraform, Helm, Argo CD,
  Argo Rollouts, AWS Load Balancer Controller)
- Added sections: Environments & Release Process

1.0.0 → 1.1.0
- Modified sections: Technology Stack Constraints (added AWS / Amazon EKS / Amazon
  RDS-Aurora PostgreSQL hosting requirements)

[template] → 1.0.0 (initial ratification)
- Added principles: I. Test-First Development; II. Patient Data Protection & RODO
  Compliance; III. Full Auditability; IV. Mobile-First Design; V. Risk-Tiered High
  Availability
- Added sections: Technology Stack Constraints; Development Workflow & Quality Gates
-->

# Dental Clinic Management System Constitution

## Core Principles

### I. Test-First Development (NON-NEGOTIABLE)
Tests MUST be written before implementation for both backend (Java) and frontend
(Angular) code. Tests MUST be shown to fail before the corresponding implementation
is written (Red-Green-Refactor). No feature branch may be merged with implementation
code that lacks a preceding failing test for its behavior.
Rationale: Scheduling, clinical record, and billing logic errors carry higher real-world
cost (missed treatment, billing disputes, patient harm) than defects in typical line-of-
business software, so regressions must be caught before they reach production.

### II. Patient Data Protection & RODO Compliance (NON-NEGOTIABLE)
Patient health data is a special category of personal data under RODO (GDPR) Art. 9.
All such data MUST be encrypted at rest and in transit. Access MUST be governed by
role-based access control (RBAC) scoped to job function (e.g. recepcja, lekarz,
administrator), following least-privilege. Data retention, export, and erasure
procedures MUST satisfy RODO subject-rights and retention requirements before any
feature touching patient data is considered done.
Rationale: Legal and regulatory obligation for a system processing medical data in the
EU; non-negotiable because violations carry direct legal liability for the clinic.

### III. Full Auditability
Every create, read, update, and delete operation on patient or clinical data MUST be
recorded in an append-only, tamper-evident audit log capturing who, what, when, and
(for writes) the before/after state. Audit logs MUST be retained per the retention
policy defined alongside RODO compliance and MUST NOT be editable or deletable by
application users, including administrators, through normal application flows.
Rationale: Explicit clinic requirement; required for compliance investigations, dispute
resolution, and clinical accountability.

### IV. Mobile-First Design
All Angular user interfaces MUST be designed and implemented mobile-first, then
progressively enhanced for tablet and desktop viewports. Core clinical and scheduling
workflows MUST remain fully usable on phone/tablet screen sizes.
Rationale: Explicit requirement — clinic staff (dentists, hygienists, reception) operate
from tablets and phones at chairside and the front desk, not exclusively desktops.

### V. Risk-Tiered High Availability
Application modules MUST be explicitly classified by risk tier. Modules handling
patient records, appointment scheduling, and billing are high-risk and MUST meet a
defined availability target with redundancy/failover, and MUST NOT share a failure
domain with lower-tier modules (e.g. reporting, internal admin configuration). Module
boundaries MUST be documented in the implementation plan for every feature that
introduces or modifies a high-risk module.
Rationale: Explicit requirement for modular separation with elevated availability for
the modules whose downtime directly blocks patient care or billing.

### VI. Infrastructure & Delivery as Code (NON-NEGOTIABLE)
All AWS infrastructure, all Kubernetes cluster/application state, and every CI/CD
pipeline MUST be defined declaratively in version control and applied only through
the tooling defined in Technology Stack Constraints. Manual ("ClickOps") changes made
directly in the AWS Console, via kubectl against a live cluster, or through manual
edits to CI/CD pipeline configuration in a web UI are FORBIDDEN except for
break-glass incident response, and any such emergency change MUST be reconciled back
into code within one business day. Every change of any kind — application code,
infrastructure, cluster/deployment config, or the pipelines themselves — MUST be
applied only by running through the pipeline defined in code (Development Workflow &
Quality Gates); there is no supported path to production that bypasses it.
Rationale: Explicit requirement that the full system — application, infrastructure,
and the delivery process itself — be reproducible, reviewable, and auditable from
version control, with zero manual/click-driven steps anywhere, consistent with
Principle III (Full Auditability).

## Technology Stack Constraints

- Frontend: Angular (current LTS), built mobile-first per Principle IV.
- Backend: Java.
- Database: PostgreSQL.
- Hosting: AWS. The application MUST be deployed on Amazon EKS (AWS-managed
  Kubernetes); self-managed Kubernetes clusters or non-AWS clouds MUST NOT be used.
- Database hosting: PostgreSQL MUST run on an AWS-managed PostgreSQL service (Amazon
  RDS for PostgreSQL or Aurora PostgreSQL), not a self-hosted PostgreSQL instance
  inside the cluster or elsewhere.
- Infrastructure provisioning: Terraform is the required IaC tool for all AWS
  resources (VPC, EKS, RDS/Aurora, IAM, networking, secrets).
- Kubernetes application delivery: Helm charts define application/cluster state;
  Argo CD applies that state via GitOps (git as source of truth, pull-based
  reconciliation). Ansible and other imperative/agent-based configuration tools
  MUST NOT be used for cluster or application deployment.
- Progressive delivery: Argo Rollouts orchestrates canary releases, shifting traffic
  in weighted percentage steps via the AWS Load Balancer Controller (ALB weighted
  target groups). A full service mesh (e.g. Istio) is out of scope unless a future
  amendment justifies the added operational complexity.
- CI/CD: GitHub Actions is the required CI/CD platform. Every pipeline MUST be
  defined as workflow files committed under `.github/workflows/` in the relevant
  repository — application build/test, Terraform plan/apply, and Helm/Argo CD
  sync/promotion all run as code-defined GitHub Actions workflows, never as manually
  configured jobs in any UI.
- These are binding technology choices for this project. Deviating from them (e.g.
  introducing a different frontend framework, backend language, hosting provider,
  primary datastore, or IaC/CI-CD/deployment toolchain) requires a constitution
  amendment, not just a plan-level decision.

## Environments & Release Process

- Two environments exist: DEV and PROD. There is no persistent shared staging
  environment beyond these two.
- DEV is ephemeral and on-demand. It MUST be provisioned from its own dedicated
  Terraform workspace/state (isolated from PROD state), triggered manually via the
  CI/CD pipeline when a change needs pre-PROD verification. DEV MUST be torn down
  (`terraform destroy`) once testing is complete rather than left running
  indefinitely. DEV MUST NOT share a cluster or failure domain with PROD, consistent
  with Principle V.
- PROD is persistent and always-on, subject to the risk-tiered availability
  requirements of Principle V.
- All PROD deployments MUST use canary progressive delivery (see Technology Stack
  Constraints): a change MUST pass through defined weighted traffic stages —
  observed for health/error-rate signals at each stage — before reaching 100% of
  PROD traffic. Deploying directly to 100% of PROD traffic without a canary phase is
  FORBIDDEN except for a documented emergency rollback to a previously-canaried
  version.
- Canary stages MUST be paired with sufficient observability (metrics/error rates)
  to make an automated or human promotion/rollback decision at each stage, and every
  promotion/rollback decision is subject to Principle III (Full Auditability).

## Development Workflow & Quality Gates

- All changes — application code, infrastructure, cluster/deployment config, and
  pipeline definitions alike — are made via pull request; no direct commits to the
  main branch of any repository.
- Every change MUST run through a GitHub Actions pipeline defined in code (Principle
  VI). CI MUST run and pass the full automated test suite (per Principle I) before
  merge, and merges to main MUST be what triggers deployment — never a manual
  action taken outside the pipeline.
- Any change touching patient data, authentication, authorization, or audit logging
  MUST receive an explicit security/compliance review before merge, verifying
  Principles II and III are upheld.
- Any change introducing or modifying a high-risk module (Principle V) MUST document
  its availability approach in the corresponding `/speckit-plan` output before
  `/speckit-implement` proceeds.
- Code review by at least one other contributor is required before merge.

## Governance

This constitution supersedes all other project practices, templates, and informal
conventions where they conflict. Amendments require: (1) a documented rationale for
the change, (2) a semantic version bump following the policy below, and (3) review/
approval before the amended constitution is merged.

Versioning policy (semantic versioning applied to this document):
- MAJOR: Backward-incompatible governance changes, or removal/redefinition of a
  principle.
- MINOR: A new principle or materially expanded section is added.
- PATCH: Wording clarifications, typo fixes, non-semantic refinements.

All pull requests and `/speckit-*` command outputs MUST be checked against these
principles for compliance; unjustified complexity or deviation must be called out
explicitly rather than silently introduced.

**Version**: 1.3.0 | **Ratified**: 2026-08-16 | **Last Amended**: 2026-08-16

# Phase 1 Data Model: Staff Auth & RBAC

Derived from spec.md Key Entities, expanded with the fields implied by the Functional Requirements
and the technical decisions in research.md. All tables live in the PostgreSQL schema owned by the
`auth-service` (see plan.md Risk Tier section on why this is its own service/schema, not shared
with lower-tier modules).

## Entities

### StaffAccount

Represents a clinic employee authorized to log in. Corresponds to spec's "Konto użytkownika
(personelu)".

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `email` | citext, unique, not null | Unique identifier for login (FR-002); also the destination for password-reset links (FR-016) and must be a *service* email address, not a patient-facing one (FR-001 — staff only). |
| `password_hash` | text, not null | Argon2id hash (research.md #6); never the plaintext password (FR-013). |
| `role` | enum: `RECEPTION` \| `DOCTOR` \| `ADMINISTRATOR`, not null | Exactly one role per account (FR-003). |
| `status` | enum: `ACTIVE` \| `DEACTIVATED`, not null, default `ACTIVE` | Deactivated accounts cannot authenticate (FR-010, FR-009). |
| `failed_login_count` | int, not null, default 0 | Incremented on each failed attempt, reset to 0 on success; drives the brute-force lockout (FR-011). |
| `locked_until` | timestamptz, nullable | Set to `now() + 15 minutes` when `failed_login_count` reaches 5 (spec Assumptions); login denied while `now() < locked_until`. |
| `mfa_enrolled` | boolean, not null, default false | MFA is mandatory (FR-015); account cannot complete login until enrollment is finished. |
| `created_at` | timestamptz, not null | |
| `created_by` | UUID, FK → StaffAccount.id, nullable | The administrator who created the account (FR-009); nullable only for any initial bootstrap/seed account. |
| `updated_at` | timestamptz, not null | |

**Validation rules**:
- `email` must be a syntactically valid, unique email address.
- `role` is required and immutable except via the explicit admin role-change flow (which produces
  an AuditLogEntry — FR-007).
- An account cannot authenticate unless `status = ACTIVE`, `now() >= locked_until` (or
  `locked_until IS NULL`), and (after password step) MFA is successfully verified.

**State transitions** (`status`):
`ACTIVE → DEACTIVATED` (admin deactivates, US3 AC2) and `DEACTIVATED → ACTIVE` (admin
reactivates, US3), both admin-only and both audit-logged (FR-007). No self-service path changes
`status`.

### MfaEnrollment

One TOTP secret per StaffAccount (research.md #4). Modeled as its own table (not inline columns
on StaffAccount) so the secret can be encrypted/rotated and access-controlled independently of the
account row.

| Field | Type | Notes |
|---|---|---|
| `account_id` | UUID, PK, FK → StaffAccount.id | One enrollment per account. |
| `totp_secret_encrypted` | bytea, not null | KMS-encrypted TOTP shared secret (FR-013 — encrypted at rest); decrypted only in-memory to verify a submitted code. |
| `enrolled_at` | timestamptz, not null | |

**Validation rules**: created only during the mandatory first-login MFA enrollment step; never
exposed (even to the owning user) after creation — only used server-side to verify a submitted
6-digit TOTP code.

### Session

Represents an authenticated, active connection (spec's "Sesja"). Backed by Spring Session JDBC
(research.md #5).

| Field | Type | Notes |
|---|---|---|
| `session_id` | text, PK | Opaque session identifier (Spring Session default). |
| `account_id` | UUID, FK → StaffAccount.id, not null | |
| `created_at` | timestamptz, not null | Session start time. |
| `last_accessed_at` | timestamptz, not null | Updated on each authenticated request; idle timeout (FR-012) computed as `now() - last_accessed_at > 15 minutes`. |
| `expires_at` | timestamptz, not null | Hard cap in addition to idle timeout: `created_at + 8 hours` (spec Assumptions), independent of activity — a session is invalidated at this timestamp even if continuously active. |

**Validation rules / lifecycle**: a session is invalidated (deleted) on: explicit logout, idle
timeout, hard expiry, the owning account being deactivated mid-session, or the owning account's
role being changed mid-session (FR-007a — Edge Cases in spec.md; the next request from that
session must be rejected and forced to re-authenticate, not merely the next login attempt).

### PasswordResetToken

Spec's "Token resetu hasła" (FR-016/017).

| Field | Type | Notes |
|---|---|---|
| `id` | UUID, PK | |
| `account_id` | UUID, FK → StaffAccount.id, not null | |
| `token_hash` | text, not null, unique | Only a hash of the token is stored (mirrors password-hash treatment — the raw token in the emailed link is a bearer secret and must not be recoverable from the DB). |
| `created_at` | timestamptz, not null | |
| `expires_at` | timestamptz, not null | `created_at + 30 minutes` (spec Assumptions). |
| `used_at` | timestamptz, nullable | Set on successful use; a token with `used_at IS NOT NULL` is rejected on any further attempt (single use, FR-016). |

**Validation rules**: a token is valid only if `used_at IS NULL AND now() < expires_at`; any
request attempt (valid or not) produces an AuditLogEntry (FR-017 — success/failure/expired).

### AuditLogEntry

Spec's "Wpis logu audytowego" — append-only, hash-chained (research.md #7).

| Field | Type | Notes |
|---|---|---|
| `id` | bigserial, PK | Monotonic — also gives a natural chain order. |
| `event_type` | enum: `LOGIN_SUCCESS` \| `LOGIN_FAILURE` \| `LOGIN_DENIED_LOCKED` \| `LOGIN_DENIED_DEACTIVATED` \| `MFA_FAILURE` \| `ROLE_CHANGED` \| `ACCOUNT_CREATED` \| `ACCOUNT_DEACTIVATED` \| `ACCOUNT_REACTIVATED` \| `PASSWORD_RESET_REQUESTED` \| `PASSWORD_RESET_SUCCEEDED` \| `PASSWORD_RESET_FAILED` \| `PASSWORD_RESET_EXPIRED` \| `ACCESS_DENIED_OUT_OF_ROLE` | Covers every FR-006/007/010/011/017 and US2/US3 audit requirement. |
| `actor_account_id` | UUID, FK → StaffAccount.id, nullable | Who performed the action; null when the actor isn't/can't be identified (e.g. failed login with an email that matches no account — FR-006 still requires an entry "if known"). |
| `target_account_id` | UUID, FK → StaffAccount.id, nullable | The account affected, when different from the actor (e.g. admin changes another user's role). |
| `occurred_at` | timestamptz, not null, default `now()` | |
| `before_state` | jsonb, nullable | E.g. previous role, for `ROLE_CHANGED` (FR-007). |
| `after_state` | jsonb, nullable | E.g. new role. |
| `metadata` | jsonb, nullable | E.g. request IP, denied resource type (never the resource's identifying content, per FR-005) — no secrets (passwords, TOTP codes, raw reset tokens) are ever written here (FR-006). |
| `previous_entry_hash` | char(64), nullable | Hash of the prior row; null only for the very first row. |
| `entry_hash` | char(64), not null | `SHA-256(previous_entry_hash \|\| event_type \|\| actor_account_id \|\| target_account_id \|\| occurred_at \|\| before_state \|\| after_state)`, computed at insert time. |

**Validation rules**: `INSERT`-only at the database grant level (no `UPDATE`/`DELETE` privilege
for the application's DB role — research.md #7); no API endpoint or UI path exists to modify or
remove a row, for any role including administrator (FR-008).

## Relationships

```text
StaffAccount 1---1 MfaEnrollment
StaffAccount 1---N Session
StaffAccount 1---N PasswordResetToken
StaffAccount 1---N AuditLogEntry (as actor_account_id)
StaffAccount 1---N AuditLogEntry (as target_account_id)
AuditLogEntry N---1 AuditLogEntry (previous_entry_hash → entry_hash, chain order)
```

## Permission matrix (summary — full detail in `contracts/rbac-policy.md`)

| Role | Wizyty i dane kontaktowe pacjentów | Dokumentacja medyczna / historia leczenia | Konta użytkowników i konfiguracja | Log audytowy |
|---|---|---|---|---|
| RECEPTION | Read/Write | No access | No access | No access |
| DOCTOR | No access | Read/Write (all patients, not just "own" — FR-014) | No access | No access |
| ADMINISTRATOR | No access (no default clinical access) | No access | Read/Write | Read (review only — never write/delete, FR-008) |

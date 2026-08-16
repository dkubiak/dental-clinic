-- AuditLogEntry (data-model.md) — append-only, hash-chained (research.md #7). Tamper-evidence
-- is enforced at two layers: (1) no API/UI path ever issues UPDATE/DELETE against this table
-- (application layer, FR-008); (2) the application's own DB role has UPDATE/DELETE revoked at
-- the grant level below, so even a compromised/buggy app instance cannot alter history — only
-- out-of-band superuser access could, which is outside this application's threat model.
--
-- `auth_service_app` is created here (idempotent) so the same migration produces an identical,
-- testable privilege boundary in Testcontainers (T057) and in RDS/Aurora. Its LOGIN password /
-- IAM-auth credential is provisioned out-of-band (Terraform + AWS Secrets Manager for RDS; the
-- test fixture for Testcontainers) — never hardcoded in a migration checked into source control.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_service_app') THEN
        CREATE ROLE auth_service_app WITH LOGIN;
    END IF;
END
$$;

CREATE TYPE audit_event_type AS ENUM (
    'LOGIN_SUCCESS',
    'LOGIN_FAILURE',
    'LOGIN_DENIED_LOCKED',
    'LOGIN_DENIED_DEACTIVATED',
    'MFA_FAILURE',
    'ROLE_CHANGED',
    'ACCOUNT_CREATED',
    'ACCOUNT_DEACTIVATED',
    'ACCOUNT_DEACTIVATION_DENIED_LAST_ADMIN', -- FR-009a
    'ACCOUNT_REACTIVATED',
    'PASSWORD_RESET_REQUESTED',
    'PASSWORD_RESET_SUCCEEDED',
    'PASSWORD_RESET_FAILED',
    'PASSWORD_RESET_EXPIRED',
    'ACCESS_DENIED_OUT_OF_ROLE'
);

CREATE TABLE audit_log_entry (
    id                   BIGSERIAL PRIMARY KEY, -- monotonic — also gives natural chain order
    event_type           audit_event_type NOT NULL,
    actor_account_id     UUID REFERENCES staff_account (id), -- nullable: e.g. failed login, unknown email
    target_account_id    UUID REFERENCES staff_account (id),
    occurred_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    before_state          JSONB,
    after_state            JSONB,
    metadata                JSONB, -- e.g. request IP; NEVER secrets (passwords, TOTP codes, raw tokens)
    previous_entry_hash    CHAR(64), -- null only for the very first row
    entry_hash              CHAR(64) NOT NULL
);

CREATE INDEX idx_audit_log_entry_occurred_at ON audit_log_entry (occurred_at);
CREATE INDEX idx_audit_log_entry_actor ON audit_log_entry (actor_account_id);
CREATE INDEX idx_audit_log_entry_target ON audit_log_entry (target_account_id);
CREATE INDEX idx_audit_log_entry_event_type ON audit_log_entry (event_type);

-- Tamper-evidence grant boundary (FR-008): INSERT/SELECT only, no UPDATE/DELETE, ever.
GRANT SELECT, INSERT ON audit_log_entry TO auth_service_app;
GRANT USAGE, SELECT ON SEQUENCE audit_log_entry_id_seq TO auth_service_app;
REVOKE UPDATE, DELETE ON audit_log_entry FROM auth_service_app;

-- The application role also needs ordinary CRUD on every other table it owns (not itself
-- append-only) — granted broadly here since this is the only application role in this schema.
GRANT SELECT, INSERT, UPDATE, DELETE ON
    staff_account,
    mfa_enrollment,
    password_reset_token,
    spring_session,
    spring_session_attributes
    TO auth_service_app;
GRANT USAGE ON ALL SEQUENCES IN SCHEMA public TO auth_service_app;

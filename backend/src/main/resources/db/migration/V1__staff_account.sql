-- StaffAccount (data-model.md) — the only login-capable identity in this system (FR-001).
CREATE EXTENSION IF NOT EXISTS citext;
CREATE EXTENSION IF NOT EXISTS "pgcrypto"; -- gen_random_uuid()

CREATE TYPE staff_role AS ENUM ('RECEPTION', 'DOCTOR', 'ADMINISTRATOR'); -- FR-003
CREATE TYPE staff_account_status AS ENUM ('ACTIVE', 'DEACTIVATED'); -- FR-009/FR-010

CREATE TABLE staff_account (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               CITEXT NOT NULL UNIQUE, -- FR-002; also password-reset destination (FR-016)
    password_hash       TEXT NOT NULL, -- Argon2id (FR-013) — never plaintext
    role                staff_role NOT NULL,
    status              staff_account_status NOT NULL DEFAULT 'ACTIVE',
    failed_login_count  INTEGER NOT NULL DEFAULT 0, -- FR-011
    locked_until        TIMESTAMPTZ,
    mfa_enrolled        BOOLEAN NOT NULL DEFAULT FALSE, -- FR-015
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID REFERENCES staff_account (id),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_staff_account_role ON staff_account (role);
CREATE INDEX idx_staff_account_status ON staff_account (status);

COMMENT ON TABLE staff_account IS
    'Clinic staff (recepcja/lekarz/administrator) — never patients (FR-001).';

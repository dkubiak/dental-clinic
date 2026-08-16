-- MfaEnrollment (data-model.md) — one TOTP secret per StaffAccount, encrypted via AWS KMS
-- (FR-013, T022a) and never exposed after creation, even to the owning user.
CREATE TABLE mfa_enrollment (
    account_id             UUID PRIMARY KEY REFERENCES staff_account (id) ON DELETE CASCADE,
    totp_secret_encrypted  BYTEA NOT NULL, -- KMS-encrypted; decrypted only in-memory to verify a code
    enrolled_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

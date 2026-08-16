-- PasswordResetToken (data-model.md) — FR-016/FR-017. Only a hash of the bearer token is
-- stored; the raw token in the emailed link is never recoverable from the DB.
CREATE TABLE password_reset_token (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id  UUID NOT NULL REFERENCES staff_account (id) ON DELETE CASCADE,
    token_hash  TEXT NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ NOT NULL, -- created_at + 30 minutes (spec Assumptions)
    used_at     TIMESTAMPTZ
);

CREATE INDEX idx_password_reset_token_account ON password_reset_token (account_id);

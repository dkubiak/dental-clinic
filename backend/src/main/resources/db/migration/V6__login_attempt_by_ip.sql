-- LoginAttemptByIp (data-model.md) — Postgres-backed sliding-window counter for per-IP
-- brute-force protection (FR-011b), deliberately reusing the existing RDS/Aurora instance
-- instead of introducing a new datastore (plan.md Constraints).
CREATE TABLE login_attempt_by_ip (
    ip_address     INET NOT NULL,
    window_start   TIMESTAMPTZ NOT NULL,
    attempt_count  INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (ip_address, window_start)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON login_attempt_by_ip TO auth_service_app;

-- FR-011b: /auth/login rejects with 429 once the per-IP threshold is exceeded; audit-logged.
ALTER TYPE audit_event_type ADD VALUE 'LOGIN_DENIED_RATE_LIMITED';

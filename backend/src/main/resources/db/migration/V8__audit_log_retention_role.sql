-- FR-018: audit log retention (rows older than 3 years, purged by AuditLogRetentionJob) is the
-- one sanctioned DELETE path against audit_log_entry — deliberately run under a SEPARATE,
-- more-privileged DB role than the application's normal auth_service_app, which has UPDATE/DELETE
-- revoked on this table (V5__audit_log.sql, FR-008). Isolating the credential this way means the
-- app's everyday runtime role can never delete audit history, even if the application code were
-- compromised or buggy.
--
-- `auth_service_retention` is created here (idempotent, mirroring V5's auth_service_app pattern)
-- so the same migration produces an identical, testable privilege boundary in Testcontainers and
-- in RDS/Aurora. Its credential is provisioned out-of-band (Terraform + AWS Secrets Manager for
-- RDS; the test fixture for Testcontainers) — never hardcoded in a migration checked into source
-- control.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_service_retention') THEN
        CREATE ROLE auth_service_retention WITH LOGIN;
    END IF;
END
$$;

GRANT SELECT, DELETE ON audit_log_entry TO auth_service_retention;

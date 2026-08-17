#!/bin/sh
# Pre-creates the auth_service_retention role (with a password) before Flyway runs, mirroring
# 01-create-app-role.sh / PostgresIntegrationTestBase's Testcontainers bootstrap —
# V8__audit_log_retention_role.sql's own `CREATE ROLE IF NOT EXISTS` (passwordless) then becomes
# a harmless no-op. Used only by AuditLogRetentionJob (T085a, FR-018) — a separate, more-privileged
# role than auth_service_app, which has DELETE revoked on audit_log_entry (FR-008).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_service_retention') THEN
        CREATE ROLE auth_service_retention WITH LOGIN PASSWORD '${AUTH_SERVICE_RETENTION_PASSWORD}';
    END IF;
END
\$\$;
EOSQL

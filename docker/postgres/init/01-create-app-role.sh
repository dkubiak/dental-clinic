#!/bin/sh
# Pre-creates the auth_service_app role (with a password) before Flyway runs, mirroring
# PostgresIntegrationTestBase's Testcontainers bootstrap — V5__audit_log.sql's own
# `CREATE ROLE IF NOT EXISTS` (passwordless) then becomes a harmless no-op.
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_service_app') THEN
        CREATE ROLE auth_service_app WITH LOGIN PASSWORD '${AUTH_SERVICE_APP_PASSWORD}';
    END IF;
END
\$\$;
EOSQL

#!/bin/sh
# Pre-creates the patient_service_app role (with a password) before either service's Flyway
# migrations run, mirroring 01-create-app-role.sh / PostgresIntegrationTestBase's Testcontainers
# bootstrap — patient-service's own V1__patient_record.sql and auth-service's own
# V10__patient_service_role.sql each do an idempotent `CREATE ROLE IF NOT EXISTS` (passwordless),
# which becomes a harmless no-op once this has run (research.md #7 — either migration set can run
# first).
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
DO \$\$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'patient_service_app') THEN
        CREATE ROLE patient_service_app WITH LOGIN PASSWORD '${PATIENT_SERVICE_APP_PASSWORD}';
    END IF;
END
\$\$;
EOSQL

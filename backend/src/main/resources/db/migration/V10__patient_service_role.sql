-- Idempotently creates the patient_service_app DB role used by the new, independent
-- patient-service deployable (research.md #7) — this migration (auth-service side) and
-- patient-service's own V1__patient_record.sql both create the role idempotently so either
-- service's migrations can run first; whichever runs second just adds its grants to a role that
-- may already exist. Locally, docker/postgres/init/03-create-patient-service-role.sh already
-- pre-creates it with a login password before either service's Flyway runs, making this a
-- harmless no-op in that environment — in RDS/Aurora, the LOGIN password/IAM-auth credential is
-- provisioned out-of-band (Terraform + AWS Secrets Manager), never hardcoded here.
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'patient_service_app') THEN
        CREATE ROLE patient_service_app WITH LOGIN;
    END IF;
END
$$;

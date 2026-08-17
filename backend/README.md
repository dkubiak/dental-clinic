# auth-service

Staff authentication, MFA, and RBAC foundation for the dental clinic system (feature
`001-staff-auth-rbac`). See `specs/001-staff-auth-rbac/` in the repo root for the full spec, plan,
and task breakdown.

## Risk tier & availability (Principle V)

**Module**: Staff Authentication & RBAC — classified **high-risk**, on the same tier as patient
records, scheduling, and billing. Every one of those modules depends on this service to authorize
every request; an outage here is an outage for all of them.

**Failure domain isolation**:

- Deployed as its own Helm release (`helm/auth-service/`), its own Deployment and HPA — never
  co-scheduled or bundled with lower-tier modules (e.g. reporting, internal admin config UIs), so
  that a lower-tier module's resource exhaustion or crash cannot take down login/RBAC enforcement.
  Any future feature adding a lower-tier module must deploy as a separate Helm release from
  `auth-service`, `patient-records`, `scheduling`, and `billing`.
- Runs with ≥2 replicas across availability zones behind the ALB (`helm/auth-service/values.yaml`).
- Session state lives in the same RDS/Aurora Postgres instance the service already requires
  (Spring Session JDBC), avoiding a second stateful dependency. RDS/Aurora Multi-AZ failover
  (standard AWS capability) covers the database failure domain.

## Database roles

Three distinct, least-privilege Postgres roles back this service — never share credentials across
them:

| Role | Used by | Privileges | Migration |
|---|---|---|---|
| `auth_service_app` | The application's normal runtime (JPA, Spring Session) | `SELECT/INSERT` on `audit_log_entry` (no `UPDATE`/`DELETE` — FR-008); full CRUD on every other table | `V5__audit_log.sql` |
| `auth_service_retention` | `AuditLogRetentionJob` only | `SELECT/DELETE` on `audit_log_entry` only | `V8__audit_log_retention_role.sql` |
| Flyway migration user | Schema migrations only, run once at startup | Schema owner / superuser | provisioned out-of-band (Terraform for RDS; container superuser for Testcontainers/local) |

Splitting `auth_service_app` from `auth_service_retention` means the application's everyday
runtime credential can never delete audit history (FR-008), even if the application code were
compromised or buggy — the only sanctioned deletion path (the 3-year retention purge, FR-018) runs
under a separate credential with no other capability.

## Scheduled jobs

- `AuditLogRetentionJob` (`com.dentalclinic.auth.auditlog.AuditLogRetentionJob`) — deletes
  `audit_log_entry` rows older than `app.retention.max-age` (default 3 years / FR-018) on the cron
  schedule `app.retention.cron` (default daily at 03:00). Logs its own run (row count, cutoff) via
  SLF4J only — never back into the audit log itself, since logging a purge inside the log being
  purged would be circular.

## Local development

See the repo root `docker-compose.yml` for the full local stack (Postgres + auth-service +
frontend). `auth-service` runs with `SPRING_PROFILES_ACTIVE=e2e-seed`, which stubs AWS
KMS/SES in-process and seeds three quickstart.md test accounts
(`reception@clinic.test` / `doctor@clinic.test` / `admin@clinic.test`, password
`correct-horse-battery-staple`).

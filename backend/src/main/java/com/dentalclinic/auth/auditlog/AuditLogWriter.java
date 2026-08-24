package com.dentalclinic.auth.auditlog;

import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Computes and appends the audit log's hash chain (T025; research.md #7). This is the only class in
 * the codebase permitted to construct an {@link AuditLogEntry} — every other service (AuthService,
 * AccountAdminService, PasswordResetService, ...) MUST record events through this writer, never by
 * touching {@link AuditLogEntryRepository} directly, so the chain can never be built incorrectly
 * (e.g. with a stale {@code previousEntryHash}).
 *
 * <p>"Read chain tail → compute hash → insert" is serialized by a Postgres transaction-scoped
 * advisory lock ({@code pg_advisory_xact_lock}, research.md #5a of 002-patient-records), not the
 * in-process {@code synchronized} this class originally used. {@code synchronized} only serialized
 * calls made through the same JVM object — it could never have coordinated {@code auth-service}'s
 * own ≥2 replicas (separate JVMs) with each other, and once {@code patient-service} became a
 * second, independent writer to this same table (via {@code PatientAuditWriter}, its own class in a
 * separate deployable), the gap became unavoidable to fix. An advisory lock is coordinated by the
 * database itself, so it correctly serializes writers regardless of which process or service holds
 * them — no new infrastructure (e.g. a distributed lock service) is introduced. The lock is
 * acquired explicitly via {@link JdbcTemplate} (rather than relying on the {@code @Transactional}
 * annotation + an injected Spring-proxy bean) so this class's transactional behavior is correct
 * even when a caller constructs it directly with {@code new} against a shared {@link
 * AuditLogEntryRepository} (as {@code AuditLogWriterConcurrencyTest} deliberately does, to simulate
 * two independent writer processes) — {@link TransactionTemplate} demarcates one real database
 * transaction per {@link #append} call regardless of how the object was obtained.
 */
@Service
public class AuditLogWriter {

  // Arbitrary, fixed 64-bit key identifying "the audit log hash chain" as the thing being
  // serialized — pg_advisory_xact_lock(bigint) locks are scoped by this key alone (not by table
  // or row), so every writer (auth-service's own replicas, patient-service) MUST use this exact
  // same constant for the lock to actually contend against every other writer.
  private static final long HASH_CHAIN_ADVISORY_LOCK_KEY = 8743028174562911L;

  private final AuditLogEntryRepository repository;
  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public AuditLogWriter(
      AuditLogEntryRepository repository,
      DataSource dataSource,
      PlatformTransactionManager transactionManager) {
    this.repository = repository;
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public AuditLogEntry append(
      AuditEventType eventType,
      UUID actorAccountId,
      UUID targetAccountId,
      String beforeStateJson,
      String afterStateJson,
      String metadataJson) {
    return transactionTemplate.execute(
        status -> {
          // Held for the remainder of this transaction (released automatically at commit/
          // rollback) — every other writer using this same key blocks here until this
          // transaction ends, so "read tail" below always sees a tail no other in-flight
          // writer can still change.
          jdbcTemplate.execute(
              "SELECT pg_advisory_xact_lock(" + HASH_CHAIN_ADVISORY_LOCK_KEY + ")");

          Instant occurredAt = Instant.now();
          String previousHash =
              repository.findLatest().map(AuditLogEntry::getEntryHash).orElse(null);
          String entryHash =
              AuditEntryHash.compute(
                  previousHash,
                  eventType,
                  actorAccountId,
                  targetAccountId,
                  occurredAt,
                  beforeStateJson,
                  afterStateJson);

          AuditLogEntry entry =
              new AuditLogEntry(
                  eventType,
                  actorAccountId,
                  targetAccountId,
                  occurredAt,
                  beforeStateJson,
                  afterStateJson,
                  metadataJson,
                  previousHash,
                  entryHash);
          return repository.save(entry);
        });
  }
}

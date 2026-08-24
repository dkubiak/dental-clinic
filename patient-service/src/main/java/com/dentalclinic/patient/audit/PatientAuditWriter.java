package com.dentalclinic.patient.audit;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Writes to the *same*, single {@code audit_log_entry} table {@code auth-service} owns (research.md
 * #5) — this service never creates a second, parallel audit table. Populates {@code
 * target_patient_record_id} (never {@code target_account_id}, which stays null for every row this
 * writer produces — exactly one of the two target columns is non-null per row, data-model.md).
 *
 * <p>Uses the identical {@code pg_advisory_xact_lock} pattern as auth-service's own {@code
 * AuditLogWriter} (research.md #5a) — held for "read chain tail → compute hash → insert" via the
 * <strong>same fixed lock key</strong> ({@link #HASH_CHAIN_ADVISORY_LOCK_KEY}, which MUST match
 * auth-service's constant exactly), so every writer to this table — auth-service's own ≥2 replicas,
 * and this service — is serialized against every other writer regardless of which process or
 * service holds the lock. No JPA entity for {@code audit_log_entry} exists in this service (it
 * doesn't own that table's migration history — patient-service's own Flyway history only creates
 * {@code patient_record}/{@code tooth_state}); plain JDBC avoids Hibernate schema validation trying
 * to reason about a table this service doesn't migrate.
 */
@Component
public class PatientAuditWriter {

  // MUST equal backend/.../auditlog/AuditLogWriter.HASH_CHAIN_ADVISORY_LOCK_KEY exactly — the
  // lock only actually serializes writers that contend on the same key.
  private static final long HASH_CHAIN_ADVISORY_LOCK_KEY = 8743028174562911L;

  private final JdbcTemplate jdbcTemplate;
  private final TransactionTemplate transactionTemplate;

  public PatientAuditWriter(DataSource dataSource, PlatformTransactionManager transactionManager) {
    this.jdbcTemplate = new JdbcTemplate(dataSource);
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  public void append(
      PatientAuditEventType eventType,
      UUID actorAccountId,
      UUID targetPatientRecordId,
      String beforeStateJson,
      String afterStateJson,
      String metadataJson) {
    transactionTemplate.executeWithoutResult(
        status -> {
          jdbcTemplate.execute(
              "SELECT pg_advisory_xact_lock(" + HASH_CHAIN_ADVISORY_LOCK_KEY + ")");

          String previousHash =
              jdbcTemplate.query(
                  "SELECT entry_hash FROM audit_log_entry ORDER BY id DESC LIMIT 1",
                  rs -> rs.next() ? rs.getString(1) : null);

          Instant occurredAt = Instant.now();
          String entryHash =
              PatientAuditEntryHash.compute(
                  previousHash,
                  eventType,
                  actorAccountId,
                  null, // target_account_id — always null for patient-scoped events
                  occurredAt,
                  beforeStateJson,
                  afterStateJson);

          jdbcTemplate.update(
              connection -> {
                PreparedStatement ps =
                    connection.prepareStatement(
                        """
                        INSERT INTO audit_log_entry
                          (event_type, actor_account_id, target_account_id,
                           target_patient_record_id, occurred_at, before_state, after_state,
                           metadata, previous_entry_hash, entry_hash)
                        VALUES (?::audit_event_type, ?, NULL, ?, ?, ?::jsonb, ?::jsonb, ?::jsonb, ?, ?)
                        """);
                ps.setString(1, eventType.name());
                setNullableUuid(ps, 2, actorAccountId);
                setNullableUuid(ps, 3, targetPatientRecordId);
                ps.setTimestamp(4, Timestamp.from(occurredAt));
                ps.setString(5, beforeStateJson);
                ps.setString(6, afterStateJson);
                ps.setString(7, metadataJson);
                if (previousHash == null) {
                  ps.setNull(8, Types.CHAR);
                } else {
                  ps.setString(8, previousHash);
                }
                ps.setString(9, entryHash);
                return ps;
              });
        });
  }

  private void setNullableUuid(PreparedStatement ps, int index, UUID value) throws SQLException {
    if (value == null) {
      ps.setNull(index, Types.OTHER);
    } else {
      ps.setObject(index, value);
    }
  }
}

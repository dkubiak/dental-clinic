package com.dentalclinic.auth.auditlog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Spec's "Wpis logu audytowego" — append-only, hash-chained (FR-006/007/008; data-model.md;
 * research.md #7). Rows are created exclusively via {@link AuditLogWriter} (T025), never mutated
 * afterwards — there is intentionally no setter on any field except through the constructor.
 */
@Entity
@Table(name = "audit_log_entry")
public class AuditLogEntry {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // @JdbcTypeCode(SqlTypes.NAMED_ENUM): audit_event_type is a native Postgres enum type
  // (V5__audit_log.sql) — see StaffAccount.role's identical comment for why this is needed.
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "event_type", nullable = false)
  private AuditEventType eventType;

  @Column(name = "actor_account_id")
  private UUID actorAccountId;

  @Column(name = "target_account_id")
  private UUID targetAccountId;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "before_state")
  private String beforeState;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "after_state")
  private String afterState;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "metadata")
  private String metadata;

  // @JdbcTypeCode(SqlTypes.CHAR) matches V5__audit_log.sql's CHAR(64) exactly — without it
  // Hibernate's schema validation (spring.jpa.hibernate.ddl-auto: validate) maps a plain String
  // field to VARCHAR regardless of columnDefinition text, and fails to start against a real
  // CHAR(64) column.
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "previous_entry_hash", length = 64)
  private String previousEntryHash;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "entry_hash", nullable = false, length = 64)
  private String entryHash;

  protected AuditLogEntry() {
    // JPA
  }

  public AuditLogEntry(
      AuditEventType eventType,
      UUID actorAccountId,
      UUID targetAccountId,
      Instant occurredAt,
      String beforeState,
      String afterState,
      String metadata,
      String previousEntryHash,
      String entryHash) {
    this.eventType = eventType;
    this.actorAccountId = actorAccountId;
    this.targetAccountId = targetAccountId;
    this.occurredAt = occurredAt;
    this.beforeState = beforeState;
    this.afterState = afterState;
    this.metadata = metadata;
    this.previousEntryHash = previousEntryHash;
    this.entryHash = entryHash;
  }

  public Long getId() {
    return id;
  }

  public AuditEventType getEventType() {
    return eventType;
  }

  public UUID getActorAccountId() {
    return actorAccountId;
  }

  public UUID getTargetAccountId() {
    return targetAccountId;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  public String getBeforeState() {
    return beforeState;
  }

  public String getAfterState() {
    return afterState;
  }

  public String getMetadata() {
    return metadata;
  }

  public String getPreviousEntryHash() {
    return previousEntryHash;
  }

  public String getEntryHash() {
    return entryHash;
  }
}

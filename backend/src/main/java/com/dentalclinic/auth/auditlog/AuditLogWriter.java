package com.dentalclinic.auth.auditlog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes and appends the audit log's hash chain (T025; research.md #7). This is the only class in
 * the codebase permitted to construct an {@link AuditLogEntry} — every other service (AuthService,
 * AccountAdminService, PasswordResetService, ...) MUST record events through this writer, never by
 * touching {@link AuditLogEntryRepository} directly, so the chain can never be built incorrectly
 * (e.g. with a stale {@code previousEntryHash}).
 *
 * <p>{@code synchronized}: at this system's scale (tens of staff accounts, spec.md Scale/Scope), a
 * simple in-process lock around "read the chain tail, then append" is sufficient to prevent two
 * concurrent writes from computing a hash against the same stale tail; a distributed lock would be
 * over-engineering for a single-instance-writes-at-a-time audit log at this volume.
 */
@Service
public class AuditLogWriter {

  private final AuditLogEntryRepository repository;

  public AuditLogWriter(AuditLogEntryRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public synchronized AuditLogEntry append(
      AuditEventType eventType,
      UUID actorAccountId,
      UUID targetAccountId,
      String beforeStateJson,
      String afterStateJson,
      String metadataJson) {
    Instant occurredAt = Instant.now();
    String previousHash = repository.findLatest().map(AuditLogEntry::getEntryHash).orElse(null);
    String entryHash =
        computeHash(
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
  }

  /**
   * {@code entry_hash = SHA-256(previous_entry_hash || event_type || actor_account_id ||
   * target_account_id || occurred_at || before_state || after_state)} (data-model.md).
   */
  private String computeHash(
      String previousHash,
      AuditEventType eventType,
      UUID actorAccountId,
      UUID targetAccountId,
      Instant occurredAt,
      String beforeStateJson,
      String afterStateJson) {
    String payload =
        String.join(
            "|",
            nullToEmpty(previousHash),
            eventType.name(),
            nullToEmpty(actorAccountId),
            nullToEmpty(targetAccountId),
            occurredAt.toString(),
            nullToEmpty(beforeStateJson),
            nullToEmpty(afterStateJson));
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      // SHA-256 is guaranteed available on every JVM (Java Cryptography Architecture standard
      // algorithm) — this can only indicate a broken JVM installation.
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }
}

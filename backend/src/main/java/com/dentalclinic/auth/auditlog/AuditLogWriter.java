package com.dentalclinic.auth.auditlog;

import java.time.Instant;
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
  }
}

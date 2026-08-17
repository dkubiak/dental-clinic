package com.dentalclinic.auth.api;

import com.dentalclinic.auth.auditlog.AuditLogEntry;
import java.time.Instant;
import java.util.UUID;

/** Read-only projection of {@link AuditLogEntry} for {@code GET /audit-log} (T063, US2). */
public record AuditLogEntryDto(
    Long id,
    String eventType,
    UUID actorAccountId,
    UUID targetAccountId,
    Instant occurredAt,
    String beforeState,
    String afterState,
    String metadata) {

  static AuditLogEntryDto from(AuditLogEntry entry) {
    return new AuditLogEntryDto(
        entry.getId(),
        entry.getEventType().name(),
        entry.getActorAccountId(),
        entry.getTargetAccountId(),
        entry.getOccurredAt(),
        entry.getBeforeState(),
        entry.getAfterState(),
        entry.getMetadata());
  }
}

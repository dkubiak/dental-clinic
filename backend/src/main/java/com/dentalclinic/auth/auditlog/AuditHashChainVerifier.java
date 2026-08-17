package com.dentalclinic.auth.auditlog;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

/**
 * T062 — recomputes the audit log's hash chain (research.md #7) to detect a tampered or missing
 * row, independent of the database-grant tamper-evidence layer (V5__audit_log.sql). Walks entries
 * in ascending {@code id} order (the chain's natural order, data-model.md) and confirms each row's
 * {@code entry_hash} still matches what {@link AuditEntryHash#compute} would produce from its
 * fields and the previous row's hash — a match proves neither this row nor anything before it (up
 * to the point of divergence) has been altered since it was written.
 */
@Component
public class AuditHashChainVerifier {

  private final AuditLogEntryRepository repository;

  public AuditHashChainVerifier(AuditLogEntryRepository repository) {
    this.repository = repository;
  }

  /** Verifies every row currently in the table, in chain order. */
  public VerificationResult verifyAll() {
    return verify(repository.findAll(Sort.by("id")));
  }

  /**
   * Verifies a specific, already-loaded sequence of entries, assumed to be in ascending chain order
   * — split out from {@link #verifyAll()} so unit tests (T058) can exercise the chain logic against
   * fabricated entries without a database.
   */
  public VerificationResult verify(List<AuditLogEntry> entriesInChainOrder) {
    String expectedPreviousHash = null;
    for (AuditLogEntry entry : entriesInChainOrder) {
      if (!Objects.equals(expectedPreviousHash, entry.getPreviousEntryHash())) {
        return VerificationResult.brokenAt(entry.getId(), "previous_entry_hash does not match");
      }
      String recomputed =
          AuditEntryHash.compute(
              entry.getPreviousEntryHash(),
              entry.getEventType(),
              entry.getActorAccountId(),
              entry.getTargetAccountId(),
              entry.getOccurredAt(),
              entry.getBeforeState(),
              entry.getAfterState());
      if (!recomputed.equals(entry.getEntryHash())) {
        return VerificationResult.brokenAt(entry.getId(), "entry_hash does not match its content");
      }
      expectedPreviousHash = entry.getEntryHash();
    }
    return VerificationResult.intact();
  }

  /** {@code brokenAtEntryId} is empty when {@link #valid} is {@code true}. */
  public record VerificationResult(boolean valid, Optional<Long> brokenAtEntryId, String detail) {

    static VerificationResult intact() {
      return new VerificationResult(true, Optional.empty(), "chain intact");
    }

    static VerificationResult brokenAt(Long entryId, String detail) {
      return new VerificationResult(false, Optional.ofNullable(entryId), detail);
    }
  }
}

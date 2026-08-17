package com.dentalclinic.auth.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * T058 — unit test for {@link AuditHashChainVerifier} (research.md #7): confirms it validates an
 * intact chain and detects both a tampered row (content changed, hash left stale) and a missing row
 * (a gap in the {@code previous_entry_hash} linkage). No Spring context / database needed — {@link
 * AuditHashChainVerifier#verify(List)} operates on plain, already-loaded entries.
 */
class AuditHashChainTest {

  private final AuditHashChainVerifier verifier = new AuditHashChainVerifier(null);

  @Test
  void intactChain_isValid() {
    AuditHashChainVerifier.VerificationResult result = verifier.verify(buildChain(3));

    assertThat(result.valid()).isTrue();
  }

  @Test
  void contentTamperedAfterInsert_isDetected() {
    List<AuditLogEntry> chain = new ArrayList<>(buildChain(3));
    AuditLogEntry original = chain.get(1);
    // Simulates a row whose content was altered by a direct SQL UPDATE after insert (the
    // stored entry_hash still reflects the ORIGINAL content, not this tampered actor id) — since
    // AuditLogEntry has no setters (tamper-evidence by construction), a fresh instance with the
    // same stored hash but different content is how that scenario is represented here.
    AuditLogEntry tampered =
        new AuditLogEntry(
            original.getEventType(),
            UUID.randomUUID(),
            original.getTargetAccountId(),
            original.getOccurredAt(),
            original.getBeforeState(),
            original.getAfterState(),
            original.getMetadata(),
            original.getPreviousEntryHash(),
            original.getEntryHash());
    chain.set(1, tampered);

    AuditHashChainVerifier.VerificationResult result = verifier.verify(chain);

    assertThat(result.valid()).isFalse();
    assertThat(result.brokenAtEntryId()).isEmpty(); // ids were never assigned in this fixture
    assertThat(result.detail()).contains("entry_hash");
  }

  @Test
  void missingRow_isDetected() {
    List<AuditLogEntry> chain = buildChain(3);
    List<AuditLogEntry> withGap = List.of(chain.get(0), chain.get(2)); // omit index 1

    AuditHashChainVerifier.VerificationResult result = verifier.verify(withGap);

    assertThat(result.valid()).isFalse();
    assertThat(result.detail()).contains("previous_entry_hash");
  }

  private static List<AuditLogEntry> buildChain(int count) {
    List<AuditLogEntry> chain = new ArrayList<>();
    String previousHash = null;
    Instant base = Instant.parse("2026-01-01T00:00:00Z");
    for (int i = 0; i < count; i++) {
      UUID actor = UUID.randomUUID();
      Instant occurredAt = base.plusSeconds(i);
      String hash =
          AuditEntryHash.compute(
              previousHash, AuditEventType.LOGIN_SUCCESS, actor, null, occurredAt, null, null);
      chain.add(
          new AuditLogEntry(
              AuditEventType.LOGIN_SUCCESS,
              actor,
              null,
              occurredAt,
              null,
              null,
              null,
              previousHash,
              hash));
      previousHash = hash;
    }
    return chain;
  }
}

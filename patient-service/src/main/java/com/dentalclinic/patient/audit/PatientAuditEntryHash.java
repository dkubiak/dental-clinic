package com.dentalclinic.patient.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Byte-for-byte duplicate of {@code auth-service}'s package-private {@code AuditEntryHash}
 * (backend/.../auditlog/AuditEntryHash.java) — MUST compute an identical hash for identical inputs,
 * since both services append to the *same* hash chain (research.md #5) and {@code
 * AuditHashChainVerifier} (auth-service) recomputes every row's hash with that one formula
 * regardless of which service wrote it. No shared library exists between the two services yet
 * (plan.md), hence the duplication rather than a dependency.
 *
 * <p>{@code entry_hash = SHA-256(previous_entry_hash || event_type || actor_account_id ||
 * target_account_id || occurred_at || before_state || after_state)} (data-model.md). Rows this
 * service writes always have a null {@code target_account_id} (they use {@code
 * target_patient_record_id} instead, which is deliberately NOT part of the hash input, exactly
 * matching auth-service's formula) — that slot simply hashes as an empty string, same as any
 * auth-service row where it's null (e.g. a failed login with an unknown actor).
 */
final class PatientAuditEntryHash {

  private PatientAuditEntryHash() {}

  static String compute(
      String previousHash,
      PatientAuditEventType eventType,
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
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static String nullToEmpty(Object value) {
    return value == null ? "" : value.toString();
  }
}

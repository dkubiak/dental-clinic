package com.dentalclinic.auth.auditlog;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * The single source of truth for the audit log's hash-chain formula (data-model.md {@code
 * entry_hash}), shared by {@link AuditLogWriter} (computes the hash when appending a row) and
 * {@link AuditHashChainVerifier} (recomputes it later to detect tampering) — both MUST agree on
 * this formula or the verifier would report false tampering on every legitimately-written row.
 */
final class AuditEntryHash {

  private AuditEntryHash() {}

  /**
   * {@code entry_hash = SHA-256(previous_entry_hash || event_type || actor_account_id ||
   * target_account_id || occurred_at || before_state || after_state)} (data-model.md).
   */
  static String compute(
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

package com.dentalclinic.auth.mfa;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * One TOTP secret per StaffAccount (data-model.md; research.md #4). {@code totpSecret} is
 * transparently KMS-encrypted at the JDBC layer by {@link TotpSecretConverter} (T022a, FR-013) —
 * this class only ever sees the plaintext secret in memory, never the ciphertext.
 */
@Entity
@Table(name = "mfa_enrollment")
public class MfaEnrollment {

  @Id
  @Column(name = "account_id")
  private UUID accountId;

  @Column(name = "totp_secret_encrypted", nullable = false)
  @Convert(converter = TotpSecretConverter.class)
  private String totpSecret;

  @Column(name = "enrolled_at", nullable = false)
  private Instant enrolledAt;

  protected MfaEnrollment() {
    // JPA
  }

  public MfaEnrollment(UUID accountId, String totpSecret) {
    this.accountId = accountId;
    this.totpSecret = totpSecret;
    this.enrolledAt = Instant.now();
  }

  public UUID getAccountId() {
    return accountId;
  }

  public String getTotpSecret() {
    return totpSecret;
  }

  public Instant getEnrolledAt() {
    return enrolledAt;
  }
}

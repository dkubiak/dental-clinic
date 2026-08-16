package com.dentalclinic.auth.passwordreset;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Spec's "Token resetu hasła" (FR-016/FR-017). Only {@link #tokenHash} is persisted — the raw
 * bearer token in the emailed link is never recoverable from the database (data-model.md).
 */
@Entity
@Table(name = "password_reset_token")
public class PasswordResetToken {

  @Id private UUID id;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Column(name = "token_hash", nullable = false, unique = true)
  private String tokenHash;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt; // createdAt + 30 minutes (spec Assumptions)

  @Column(name = "used_at")
  private Instant usedAt;

  protected PasswordResetToken() {
    // JPA
  }

  public PasswordResetToken(
      UUID id, UUID accountId, String tokenHash, Instant createdAt, Instant expiresAt) {
    this.id = id;
    this.accountId = accountId;
    this.tokenHash = tokenHash;
    this.createdAt = createdAt;
    this.expiresAt = expiresAt;
  }

  /** A token is valid only if unused and not expired (data-model.md validation rules). */
  public boolean isValid(Instant now) {
    return usedAt == null && now.isBefore(expiresAt);
  }

  public void markUsed(Instant now) {
    this.usedAt = now;
  }

  public UUID getId() {
    return id;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public Instant getUsedAt() {
    return usedAt;
  }
}

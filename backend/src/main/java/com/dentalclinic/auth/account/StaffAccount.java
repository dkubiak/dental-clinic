package com.dentalclinic.auth.account;

import com.dentalclinic.auth.role.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * A clinic employee authorized to log in (spec.md "Konto użytkownika (personelu)"). See
 * data-model.md for field-level rationale and validation rules.
 */
@Entity
@Table(name = "staff_account")
public class StaffAccount {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String email; // FR-002; citext at the DB level (V1__staff_account.sql)

  @Column(name = "password_hash", nullable = false)
  private String passwordHash; // Argon2id (FR-013) — never plaintext

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private Role role; // FR-003 — exactly one

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private AccountStatus status = AccountStatus.ACTIVE;

  @Column(name = "failed_login_count", nullable = false)
  private int failedLoginCount = 0; // FR-011

  @Column(name = "locked_until")
  private Instant lockedUntil;

  @Column(name = "mfa_enrolled", nullable = false)
  private boolean mfaEnrolled = false; // FR-015

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by")
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected StaffAccount() {
    // JPA
  }

  public StaffAccount(UUID id, String email, String passwordHash, Role role, UUID createdBy) {
    this.id = id;
    this.email = email;
    this.passwordHash = passwordHash;
    this.role = role;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** An account may authenticate only if ACTIVE and not currently locked out (data-model.md). */
  public boolean canAttemptLogin(Instant now) {
    return status == AccountStatus.ACTIVE && (lockedUntil == null || now.isAfter(lockedUntil));
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
    touch();
  }

  public Role getRole() {
    return role;
  }

  public void setRole(Role role) {
    this.role = role;
    touch();
  }

  public AccountStatus getStatus() {
    return status;
  }

  public void setStatus(AccountStatus status) {
    this.status = status;
    touch();
  }

  public int getFailedLoginCount() {
    return failedLoginCount;
  }

  public void setFailedLoginCount(int failedLoginCount) {
    this.failedLoginCount = failedLoginCount;
  }

  public Instant getLockedUntil() {
    return lockedUntil;
  }

  public void setLockedUntil(Instant lockedUntil) {
    this.lockedUntil = lockedUntil;
  }

  public boolean isMfaEnrolled() {
    return mfaEnrolled;
  }

  public void setMfaEnrolled(boolean mfaEnrolled) {
    this.mfaEnrolled = mfaEnrolled;
    touch();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  private void touch() {
    this.updatedAt = Instant.now();
  }
}

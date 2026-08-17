package com.dentalclinic.auth.api;

import com.dentalclinic.auth.account.StaffAccount;
import java.time.Instant;
import java.util.UUID;

/** Read-only projection of {@link StaffAccount} for the {@code /accounts} endpoints (T077). */
public record AccountResponse(
    UUID id, String email, String role, String status, boolean mfaEnrolled, Instant createdAt) {

  static AccountResponse from(StaffAccount account) {
    return new AccountResponse(
        account.getId(),
        account.getEmail(),
        account.getRole().name(),
        account.getStatus().name(),
        account.isMfaEnrolled(),
        account.getCreatedAt());
  }
}

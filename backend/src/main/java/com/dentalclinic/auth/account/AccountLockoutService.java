package com.dentalclinic.auth.account;

import com.dentalclinic.auth.notification.EmailSender;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * T041 — {@code failed_login_count}/{@code locked_until} logic (FR-011). Shared by password
 * failures (AuthService, T040) and MFA failures (AuthService.completeMfaLogin, T041a) so both count
 * against the same 5-attempt/15-minute threshold (FR-011a). Also sends the lockout email
 * notification (T041c, FR-011c) at the moment an account crosses the threshold.
 */
@Service
public class AccountLockoutService {

  private final StaffAccountRepository staffAccountRepository;
  private final EmailSender emailSender;
  private final int maxFailedAttempts;
  private final Duration lockoutDuration;

  public AccountLockoutService(
      StaffAccountRepository staffAccountRepository,
      EmailSender emailSender,
      @Value("${app.account-lockout.max-failed-attempts}") int maxFailedAttempts,
      @Value("${app.account-lockout.lockout-duration}") Duration lockoutDuration) {
    this.staffAccountRepository = staffAccountRepository;
    this.emailSender = emailSender;
    this.maxFailedAttempts = maxFailedAttempts;
    this.lockoutDuration = lockoutDuration;
  }

  /**
   * Records a failed attempt (password or MFA — FR-011a). Returns {@code true} if this exact
   * attempt caused the account to become newly locked (the threshold was just reached) — the caller
   * (AuthService) reports that attempt itself as "locked" (423) rather than "invalid credentials"
   * (401), since the account is locked as of this response. If newly locked, also sends a one-time
   * lockout notification email to the account's registered address (FR-011c).
   */
  public boolean recordFailure(StaffAccount account) {
    int failedCount = account.getFailedLoginCount() + 1;
    account.setFailedLoginCount(failedCount);
    boolean justLocked = false;
    if (failedCount >= maxFailedAttempts) {
      account.setLockedUntil(Instant.now().plus(lockoutDuration));
      justLocked = true;
    }
    staffAccountRepository.save(account);

    if (justLocked) {
      emailSender.send(
          account.getEmail(),
          "Twoje konto zostało tymczasowo zablokowane",
          "Wykryto "
              + failedCount
              + " kolejnych nieudanych prób logowania na Twoje konto. Konto zostało tymczasowo"
              + " zablokowane na "
              + lockoutDuration.toMinutes()
              + " minut. Jeśli to nie Ty, skontaktuj się z administratorem.");
    }
    return justLocked;
  }

  /** Resets the failure counter on a successful authentication step (FR-011). */
  public void recordSuccess(StaffAccount account) {
    account.setFailedLoginCount(0);
    account.setLockedUntil(null);
    staffAccountRepository.save(account);
  }

  public boolean isLocked(StaffAccount account, Instant now) {
    Instant lockedUntil = account.getLockedUntil();
    return lockedUntil != null && now.isBefore(lockedUntil);
  }
}

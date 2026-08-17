package com.dentalclinic.auth.account;

import com.dentalclinic.auth.auditlog.AuditEventType;
import com.dentalclinic.auth.auditlog.AuditLogWriter;
import com.dentalclinic.auth.mfa.MfaEnrollment;
import com.dentalclinic.auth.mfa.MfaService;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T040 — verifies email/password (step 1 of login) and issues a pre-auth token valid for exactly 5
 * minutes (FR-015a). Per-IP rate limiting (FR-011b) is checked first, before credential
 * verification, in the same transaction as the login attempt (data-model.md {@code
 * LoginAttemptByIp}). Audit logging (T048) for every branch below is done inline — this is the one
 * place that observes all these outcomes directly, rather than reconstructing them from whichever
 * exception the controller happens to catch.
 */
@Service
public class AuthService {

  private final StaffAccountRepository staffAccountRepository;
  private final PasswordEncoder passwordEncoder;
  private final PreAuthTokenService preAuthTokenService;
  private final AccountLockoutService accountLockoutService;
  private final IpRateLimitService ipRateLimitService;
  private final MfaService mfaService;
  private final AuditLogWriter auditLogWriter;

  public AuthService(
      StaffAccountRepository staffAccountRepository,
      PasswordEncoder passwordEncoder,
      PreAuthTokenService preAuthTokenService,
      AccountLockoutService accountLockoutService,
      IpRateLimitService ipRateLimitService,
      MfaService mfaService,
      AuditLogWriter auditLogWriter) {
    this.staffAccountRepository = staffAccountRepository;
    this.passwordEncoder = passwordEncoder;
    this.preAuthTokenService = preAuthTokenService;
    this.accountLockoutService = accountLockoutService;
    this.ipRateLimitService = ipRateLimitService;
    this.mfaService = mfaService;
    this.auditLogWriter = auditLogWriter;
  }

  /**
   * {@code noRollbackFor}: every exception below is thrown only AFTER this method has already
   * written the outcome it represents (an audit entry, a lockout-counter increment, a rate-limit
   * counter increment) — these are the actual business record of what happened and must be
   * committed, not undone by Spring's default rollback-on-RuntimeException behavior. Without this,
   * every failed login attempt would silently vanish (audit entry lost, lockout counter never
   * actually incremented) even though the caller correctly sees the failure response.
   *
   * @throws RateLimitExceededException FR-011b — source IP over the per-IP threshold.
   * @throws AccountDeactivatedException FR-010.
   * @throws AccountLockedException FR-011/FR-011a.
   * @throws InvalidCredentialsException wrong email or wrong password (indistinguishable —
   *     Acceptance Scenario US1-4).
   */
  @Transactional(
      noRollbackFor = {
        RateLimitExceededException.class,
        AccountDeactivatedException.class,
        AccountLockedException.class,
        InvalidCredentialsException.class
      })
  public LoginResult login(String email, String rawPassword, String sourceIp) {
    if (!ipRateLimitService.isAllowed(sourceIp)) {
      auditLogWriter.append(
          AuditEventType.LOGIN_DENIED_RATE_LIMITED, null, null, null, null, metadataIp(sourceIp));
      throw new RateLimitExceededException();
    }

    StaffAccount account = staffAccountRepository.findByEmail(email).orElse(null);
    if (account == null) {
      auditLogWriter.append(
          AuditEventType.LOGIN_FAILURE, null, null, null, null, metadataIp(sourceIp));
      throw new InvalidCredentialsException();
    }

    if (account.getStatus() == AccountStatus.DEACTIVATED) {
      auditLogWriter.append(
          AuditEventType.LOGIN_DENIED_DEACTIVATED,
          account.getId(),
          null,
          null,
          null,
          metadataIp(sourceIp));
      throw new AccountDeactivatedException();
    }

    Instant now = Instant.now();
    if (accountLockoutService.isLocked(account, now)) {
      auditLogWriter.append(
          AuditEventType.LOGIN_DENIED_LOCKED,
          account.getId(),
          null,
          null,
          null,
          metadataIp(sourceIp));
      throw new AccountLockedException();
    }

    if (!passwordEncoder.matches(rawPassword, account.getPasswordHash())) {
      boolean justLocked = accountLockoutService.recordFailure(account);
      auditLogWriter.append(
          AuditEventType.LOGIN_FAILURE, account.getId(), null, null, null, metadataIp(sourceIp));
      // The attempt that crosses the threshold is itself reported as "locked" (423), not
      // "invalid credentials" (401) — the account is locked as of this exact response.
      throw justLocked ? new AccountLockedException() : new InvalidCredentialsException();
    }

    accountLockoutService.recordSuccess(account);

    MfaEnrollment enrollment = mfaService.ensureEnrollment(account.getId());
    String preAuthToken = preAuthTokenService.issue(account.getId());

    String secretForSetup = null;
    String otpAuthUriForSetup = null;
    if (!account.isMfaEnrolled()) {
      secretForSetup = enrollment.getTotpSecret();
      otpAuthUriForSetup = mfaService.buildOtpAuthUri(account.getEmail(), secretForSetup);
    }

    // LOGIN_SUCCESS is written once the MFA step also succeeds (MfaService/MfaController, T045) —
    // the password step alone does not yet constitute a successful login (FR-015 mandatory MFA).
    return new LoginResult(preAuthToken, secretForSetup, otpAuthUriForSetup);
  }

  /**
   * Step 2 of login — verifies the TOTP code against the pre-auth token (FR-015a) and, on success,
   * finishes the account's MFA enrollment if this was its first successful code (data-model.md).
   * Re-checks locked/deactivated state since it may have changed between step 1 and step 2.
   *
   * @throws InvalidPreAuthTokenException token missing/malformed/tampered/expired (FR-015a).
   * @throws AccountDeactivatedException FR-010 — re-checked at this step too.
   * @throws AccountLockedException FR-011/FR-011a — re-checked at this step too.
   * @throws InvalidMfaCodeException wrong or expired TOTP code; counts toward the shared lockout
   *     counter (FR-011a, T041a).
   */
  @Transactional(
      noRollbackFor = {
        InvalidPreAuthTokenException.class,
        AccountDeactivatedException.class,
        AccountLockedException.class,
        InvalidMfaCodeException.class
      })
  public MfaVerificationResult completeMfaLogin(String preAuthToken, String code) {
    PreAuthToken decoded =
        preAuthTokenService.verify(preAuthToken).orElseThrow(InvalidPreAuthTokenException::new);

    StaffAccount account = staffAccountRepository.findById(decoded.accountId()).orElse(null);
    if (account == null || account.getStatus() == AccountStatus.DEACTIVATED) {
      throw new AccountDeactivatedException();
    }

    Instant now = Instant.now();
    if (accountLockoutService.isLocked(account, now)) {
      throw new AccountLockedException();
    }

    if (!mfaService.verifyCode(account.getId(), code)) {
      // T041a — same counter as password failures
      boolean justLocked = accountLockoutService.recordFailure(account);
      auditLogWriter.append(AuditEventType.MFA_FAILURE, account.getId(), null, null, null, null);
      throw justLocked ? new AccountLockedException() : new InvalidMfaCodeException();
    }

    accountLockoutService.recordSuccess(account);
    if (!account.isMfaEnrolled()) {
      account.setMfaEnrolled(true);
      staffAccountRepository.save(account);
    }
    auditLogWriter.append(AuditEventType.LOGIN_SUCCESS, account.getId(), null, null, null, null);

    return new MfaVerificationResult(account.getId(), account.getRole());
  }

  private static String metadataIp(String sourceIp) {
    return "{\"sourceIp\":\"" + sourceIp.replace("\"", "") + "\"}";
  }
}

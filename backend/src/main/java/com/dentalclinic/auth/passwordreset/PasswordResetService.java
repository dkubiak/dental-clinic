package com.dentalclinic.auth.passwordreset;

import com.dentalclinic.auth.account.PasswordPolicyValidator;
import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.account.StaffAccountRepository;
import com.dentalclinic.auth.auditlog.AuditEventType;
import com.dentalclinic.auth.auditlog.AuditLogWriter;
import com.dentalclinic.auth.notification.EmailSender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T043 — self-service password reset: token issuance/consumption (30-minute validity, single use,
 * FR-016), uniform response regardless of whether the email matches an account (handled by the
 * controller always returning 202 for {@link #requestReset} — this service itself sends no signal
 * back about the match either, beyond its void return), and AWS SES email delivery (FR-017 logging;
 * research.md #10).
 */
@Service
public class PasswordResetService {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StaffAccountRepository staffAccountRepository;
  private final PasswordResetTokenRepository passwordResetTokenRepository;
  private final PasswordEncoder passwordEncoder;
  private final PasswordPolicyValidator passwordPolicyValidator;
  private final EmailSender emailSender;
  private final AuditLogWriter auditLogWriter;
  private final Duration tokenValidity;
  private final String resetLinkBaseUrl;

  public PasswordResetService(
      StaffAccountRepository staffAccountRepository,
      PasswordResetTokenRepository passwordResetTokenRepository,
      PasswordEncoder passwordEncoder,
      PasswordPolicyValidator passwordPolicyValidator,
      EmailSender emailSender,
      AuditLogWriter auditLogWriter,
      @Value("${app.password-reset.token-validity}") Duration tokenValidity,
      @Value("${app.password-reset.frontend-reset-url}") String resetLinkBaseUrl) {
    this.staffAccountRepository = staffAccountRepository;
    this.passwordResetTokenRepository = passwordResetTokenRepository;
    this.passwordEncoder = passwordEncoder;
    this.passwordPolicyValidator = passwordPolicyValidator;
    this.emailSender = emailSender;
    this.auditLogWriter = auditLogWriter;
    this.tokenValidity = tokenValidity;
    this.resetLinkBaseUrl = resetLinkBaseUrl;
  }

  /**
   * Always completes without error, matching regardless of account existence or not (FR-005 via
   * FR-016) — this method's void return already gives the controller nothing to leak; the
   * uniform-202 behavior lives there. Every attempt (match or not) is audit-logged (FR-017); the
   * audit log itself is admin-only (FR-008a) so recording the truth internally does not violate the
   * external non-disclosure requirement.
   */
  @Transactional
  public void requestReset(String email) {
    StaffAccount account = staffAccountRepository.findByEmail(email).orElse(null);
    if (account == null) {
      auditLogWriter.append(AuditEventType.PASSWORD_RESET_REQUESTED, null, null, null, null, null);
      return;
    }

    String rawToken = generateRawToken();
    Instant now = Instant.now();
    PasswordResetToken token =
        new PasswordResetToken(
            UUID.randomUUID(), account.getId(), hash(rawToken), now, now.plus(tokenValidity));
    passwordResetTokenRepository.save(token);

    emailSender.send(
        account.getEmail(),
        "Reset hasła",
        "Aby zresetować hasło, otwórz: "
            + resetLinkBaseUrl
            + "?token="
            + rawToken
            + " Link jest ważny przez "
            + tokenValidity.toMinutes()
            + " minut i można go użyć tylko raz.");

    auditLogWriter.append(
        AuditEventType.PASSWORD_RESET_REQUESTED, account.getId(), null, null, null, null);
  }

  /**
   * {@code noRollbackFor}: both exceptions below are thrown only after the audit entry representing
   * that outcome has already been written — see AuthService's identical javadoc note on why this is
   * required (otherwise Spring's default rollback-on-RuntimeException would discard the very audit
   * entry FR-017 requires).
   *
   * @throws PasswordResetTokenInvalidException token unknown, expired, or already used (FR-016).
   * @throws com.dentalclinic.auth.account.PasswordPolicyException {@code newPassword} violates
   *     FR-002a.
   */
  @Transactional(
      noRollbackFor = {
        PasswordResetTokenInvalidException.class,
        com.dentalclinic.auth.account.PasswordPolicyException.class
      })
  public void confirmReset(String rawToken, String newPassword) {
    PasswordResetToken token =
        passwordResetTokenRepository.findByTokenHash(hash(rawToken)).orElse(null);
    Instant now = Instant.now();

    if (token == null) {
      auditLogWriter.append(AuditEventType.PASSWORD_RESET_FAILED, null, null, null, null, null);
      throw new PasswordResetTokenInvalidException();
    }

    if (!token.isValid(now)) {
      AuditEventType eventType =
          token.getUsedAt() != null
              ? AuditEventType.PASSWORD_RESET_FAILED
              : AuditEventType.PASSWORD_RESET_EXPIRED;
      auditLogWriter.append(eventType, token.getAccountId(), null, null, null, null);
      throw new PasswordResetTokenInvalidException();
    }

    // Validated before consuming the token so a policy-rejected password leaves the token intact
    // for a retry within its remaining validity window. A rejection here is still audited (FR-017
    // — "any request attempt, valid or not") before the exception propagates.
    try {
      passwordPolicyValidator.validate(newPassword);
    } catch (com.dentalclinic.auth.account.PasswordPolicyException e) {
      auditLogWriter.append(
          AuditEventType.PASSWORD_RESET_FAILED, token.getAccountId(), null, null, null, null);
      throw e;
    }

    StaffAccount account =
        staffAccountRepository
            .findById(token.getAccountId())
            .orElseThrow(
                () -> new IllegalStateException("Reset token referenced a missing account"));
    account.setPasswordHash(passwordEncoder.encode(newPassword));
    staffAccountRepository.save(account);

    token.markUsed(now);
    passwordResetTokenRepository.save(token);

    auditLogWriter.append(
        AuditEventType.PASSWORD_RESET_SUCCEEDED, account.getId(), null, null, null, null);
  }

  private static String generateRawToken() {
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static String hash(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }
}

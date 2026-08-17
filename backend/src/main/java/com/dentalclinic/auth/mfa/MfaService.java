package com.dentalclinic.auth.mfa;

import dev.samstevens.totp.code.CodeVerifier;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.DefaultCodeVerifier;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import dev.samstevens.totp.time.TimeProvider;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T042 — TOTP secret enrollment + code verification via {@code java-totp} (FR-015), encrypting/
 * decrypting the stored secret through T022a's {@link TotpSecretConverter} (FR-013).
 *
 * <p>Enrollment happens implicitly on first login (data-model.md — "created only during the
 * mandatory first-login MFA enrollment step"): {@link #ensureEnrollment(UUID)} is called by
 * AuthService when a not-yet-enrolled account completes the password step, generating and
 * persisting a secret exactly once (reused on repeat attempts so an abandoned enrollment doesn't
 * invalidate a QR code the user already scanned). {@code StaffAccount.mfaEnrolled} only flips to
 * {@code true} once the corresponding code is verified (MfaController/AuthService, T045), matching
 * "cannot complete login until enrollment is finished" (data-model.md).
 */
@Service
public class MfaService {

  private static final String TOTP_ISSUER = "DentalClinic";

  private final MfaEnrollmentRepository mfaEnrollmentRepository;
  private final SecretGenerator secretGenerator = new DefaultSecretGenerator();
  private final TimeProvider timeProvider = new SystemTimeProvider();
  private final CodeVerifier codeVerifier =
      new DefaultCodeVerifier(new DefaultCodeGenerator(), timeProvider);

  public MfaService(MfaEnrollmentRepository mfaEnrollmentRepository) {
    this.mfaEnrollmentRepository = mfaEnrollmentRepository;
  }

  /** Returns the account's existing secret, generating and persisting a new one if absent. */
  @Transactional
  public MfaEnrollment ensureEnrollment(UUID accountId) {
    return mfaEnrollmentRepository
        .findById(accountId)
        .orElseGet(
            () ->
                mfaEnrollmentRepository.save(
                    new MfaEnrollment(accountId, secretGenerator.generate())));
  }

  public Optional<MfaEnrollment> findEnrollment(UUID accountId) {
    return mfaEnrollmentRepository.findById(accountId);
  }

  /** Verifies a submitted 6-digit TOTP code against the account's stored secret. */
  public boolean verifyCode(UUID accountId, String code) {
    return mfaEnrollmentRepository
        .findById(accountId)
        .map(enrollment -> codeVerifier.isValidCode(enrollment.getTotpSecret(), code))
        .orElse(false);
  }

  /** {@code otpauth://} URI for authenticator-app enrollment (manual entry or QR rendering). */
  public String buildOtpAuthUri(String accountEmail, String secret) {
    String label =
        URLEncoder.encode(TOTP_ISSUER + ":" + accountEmail, StandardCharsets.UTF_8)
            .replace("+", "%20");
    String issuer = URLEncoder.encode(TOTP_ISSUER, StandardCharsets.UTF_8);
    return "otpauth://totp/"
        + label
        + "?secret="
        + secret
        + "&issuer="
        + issuer
        + "&algorithm=SHA1&digits=6&period=30";
  }
}

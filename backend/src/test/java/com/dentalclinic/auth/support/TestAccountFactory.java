package com.dentalclinic.auth.support;

import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.account.StaffAccountRepository;
import com.dentalclinic.auth.mfa.MfaEnrollment;
import com.dentalclinic.auth.mfa.MfaEnrollmentRepository;
import com.dentalclinic.auth.role.Role;
import dev.samstevens.totp.code.DefaultCodeGenerator;
import dev.samstevens.totp.code.HashingAlgorithm;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds StaffAccount/MfaEnrollment rows directly (bypassing the HTTP API) so integration tests can
 * exercise a specific login step without re-deriving the whole signup flow, and generates valid
 * TOTP codes for a given secret so MFA-dependent flows can be tested deterministically (no waiting
 * on real clock ticks or mocking a third-party authenticator app).
 */
@Component
public class TestAccountFactory {

  private final StaffAccountRepository staffAccountRepository;
  private final MfaEnrollmentRepository mfaEnrollmentRepository;
  private final PasswordEncoder passwordEncoder;

  public TestAccountFactory(
      StaffAccountRepository staffAccountRepository,
      MfaEnrollmentRepository mfaEnrollmentRepository,
      PasswordEncoder passwordEncoder) {
    this.staffAccountRepository = staffAccountRepository;
    this.mfaEnrollmentRepository = mfaEnrollmentRepository;
    this.passwordEncoder = passwordEncoder;
  }

  /** An ACTIVE account with no MFA enrollment yet (fresh, never logged in). */
  public StaffAccount createActiveAccount(String email, String rawPassword, Role role) {
    StaffAccount account =
        new StaffAccount(UUID.randomUUID(), email, passwordEncoder.encode(rawPassword), role, null);
    return staffAccountRepository.save(account);
  }

  /** An ACTIVE account that has already finished MFA enrollment; returns its raw TOTP secret. */
  public String enrollMfa(StaffAccount account) {
    String secret = new DefaultSecretGenerator().generate();
    mfaEnrollmentRepository.save(new MfaEnrollment(account.getId(), secret));
    account.setMfaEnrolled(true);
    staffAccountRepository.save(account);
    return secret;
  }

  public String currentTotpCode(String secret) {
    try {
      return new DefaultCodeGenerator(HashingAlgorithm.SHA1)
          .generate(secret, new SystemTimeProvider().getTime() / 30);
    } catch (dev.samstevens.totp.exceptions.CodeGenerationException e) {
      throw new IllegalStateException("Failed to generate a TOTP code for tests", e);
    }
  }
}

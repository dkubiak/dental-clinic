package com.dentalclinic.auth.e2eseed;

import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.account.StaffAccountRepository;
import com.dentalclinic.auth.mfa.MfaEnrollment;
import com.dentalclinic.auth.mfa.MfaEnrollmentRepository;
import com.dentalclinic.auth.role.Role;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the three test accounts quickstart.md's Prerequisites describe (one per role, MFA already
 * enrolled), each idempotently created only if not already present, and writes their credentials —
 * including the raw TOTP secret, needed to compute a valid code — to a JSON file for the Playwright
 * e2e suite (T039) to read. Active only under {@code -Dspring.profiles.active=e2e-seed} for
 * local/CI e2e runs; never in a deployed environment.
 */
@Component
@Profile("e2e-seed")
@Order(Integer.MAX_VALUE) // run after Flyway/JPA are fully initialized
public class E2eSeedRunner implements ApplicationRunner {

  private static final String SEED_PASSWORD = "correct-horse-battery-staple";

  private final StaffAccountRepository staffAccountRepository;
  private final MfaEnrollmentRepository mfaEnrollmentRepository;
  private final PasswordEncoder passwordEncoder;
  private final String outputPath;

  public E2eSeedRunner(
      StaffAccountRepository staffAccountRepository,
      MfaEnrollmentRepository mfaEnrollmentRepository,
      PasswordEncoder passwordEncoder,
      @Value("${app.e2e-seed.output-path:e2e-seed-accounts.json}") String outputPath) {
    this.staffAccountRepository = staffAccountRepository;
    this.mfaEnrollmentRepository = mfaEnrollmentRepository;
    this.passwordEncoder = passwordEncoder;
    this.outputPath = outputPath;
  }

  @Override
  public void run(ApplicationArguments args) throws IOException {
    List<SeedAccount> seeded =
        List.of(
            seed("reception@clinic.test", Role.RECEPTION),
            seed("doctor@clinic.test", Role.DOCTOR),
            seed("admin@clinic.test", Role.ADMINISTRATOR),
            // 002-patient-records T061 — lets the e2e suites prove the ASSISTANT half of
            // US1/US2 scenarios end to end (tooth-chart access, read-only basic data).
            seed("assistant@clinic.test", Role.ASSISTANT),
            // Dedicated account for the password-reset e2e scenario, which mutates its password —
            // kept separate from reception@clinic.test so parallel Playwright tests that log in
            // with that account's fixed original password aren't affected (test isolation).
            seed("password-reset-test@clinic.test", Role.RECEPTION));
    Path path = Path.of(outputPath);
    if (path.getParent() != null) {
      Files.createDirectories(path.getParent());
    }
    Files.writeString(path, toJson(seeded), StandardCharsets.UTF_8);
    System.out.println("[e2e-seed] Wrote " + seeded.size() + " seed accounts to " + outputPath);
  }

  /**
   * Hand-rolled instead of pulling in a JSON library for this dev-only fixture — five fields, no
   * nesting, no untrusted input (values are either our own literals or generated secrets/ hashes
   * with no JSON-special characters).
   */
  private static String toJson(List<SeedAccount> accounts) {
    StringBuilder json = new StringBuilder("[\n");
    for (int i = 0; i < accounts.size(); i++) {
      SeedAccount account = accounts.get(i);
      json.append("  {\n")
          .append("    \"email\": \"")
          .append(account.email())
          .append("\",\n")
          .append("    \"password\": \"")
          .append(account.password())
          .append("\",\n")
          .append("    \"totpSecret\": \"")
          .append(account.totpSecret())
          .append("\",\n")
          .append("    \"role\": \"")
          .append(account.role())
          .append("\"\n")
          .append("  }")
          .append(i < accounts.size() - 1 ? ",\n" : "\n");
    }
    json.append("]\n");
    return json.toString();
  }

  private SeedAccount seed(String email, Role role) {
    StaffAccount account =
        staffAccountRepository
            .findByEmail(email)
            .orElseGet(
                () ->
                    staffAccountRepository.save(
                        new StaffAccount(
                            UUID.randomUUID(),
                            email,
                            passwordEncoder.encode(SEED_PASSWORD),
                            role,
                            null)));

    String secret =
        mfaEnrollmentRepository
            .findById(account.getId())
            .map(MfaEnrollment::getTotpSecret)
            .orElseGet(() -> enrollMfa(account));

    return new SeedAccount(email, SEED_PASSWORD, secret, role.name());
  }

  private String enrollMfa(StaffAccount account) {
    String secret = new DefaultSecretGenerator().generate();
    mfaEnrollmentRepository.save(new MfaEnrollment(account.getId(), secret));
    account.setMfaEnrolled(true);
    staffAccountRepository.save(account);
    return secret;
  }

  /** One row of the JSON file consumed by the Playwright e2e suite. */
  public record SeedAccount(String email, String password, String totpSecret, String role) {}
}

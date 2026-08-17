package com.dentalclinic.auth.account;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * T043a — NIST 800-63B password policy (FR-002a): minimum 12 characters, rejected if present on a
 * known-breached-password list, no forced character-class complexity. Used by PasswordResetService
 * (T043) and AccountAdminService account creation (T074).
 */
@Component
public class PasswordPolicyValidator {

  public static final int MIN_LENGTH = 12;
  private static final String BREACHED_PASSWORDS_RESOURCE =
      "classpath:security/breached-passwords.txt";

  private final Set<String> breachedPasswords;

  public PasswordPolicyValidator(ResourcePatternResolver resourceResolver) {
    this.breachedPasswords = loadBreachedPasswords(resourceResolver);
  }

  /**
   * @throws PasswordPolicyException if {@code candidatePassword} violates FR-002a.
   */
  public void validate(String candidatePassword) {
    if (candidatePassword == null || candidatePassword.length() < MIN_LENGTH) {
      throw new PasswordPolicyException("Password must be at least " + MIN_LENGTH + " characters.");
    }
    if (breachedPasswords.contains(candidatePassword.toLowerCase(Locale.ROOT))) {
      throw new PasswordPolicyException("Password appears on a list of known-breached passwords.");
    }
  }

  private static Set<String> loadBreachedPasswords(ResourcePatternResolver resourceResolver) {
    Resource resource = resourceResolver.getResource(BREACHED_PASSWORDS_RESOURCE);
    Set<String> passwords = new HashSet<>();
    try (InputStream inputStream = resource.getInputStream();
        BufferedReader reader =
            new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
          passwords.add(trimmed.toLowerCase(Locale.ROOT));
        }
      }
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to load breached password list", e);
    }
    return passwords;
  }
}

package com.dentalclinic.patient.record;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * T024 — research.md #1: 11-digit format + standard Polish weighted checksum, `null` accepted
 * (PESEL is optional, FR-002).
 */
class PeselValidatorTest {

  @Test
  void nullPesel_isAccepted() {
    assertThat(PeselValidator.isValid(null)).isTrue();
  }

  @Test
  void validChecksum_isAccepted() {
    // 44051401359 — a well-known valid example PESEL (born 1944-05-14).
    assertThat(PeselValidator.isValid("44051401359")).isTrue();
  }

  @Test
  void invalidChecksum_isRejected() {
    // Same digits as the valid example above, last digit altered (US1 Acceptance Scenario 3).
    assertThat(PeselValidator.isValid("44051401350")).isFalse();
  }

  @Test
  void wrongLength_isRejected() {
    assertThat(PeselValidator.isValid("4405140135")).isFalse(); // 10 digits
    assertThat(PeselValidator.isValid("440514013599")).isFalse(); // 12 digits
  }

  @Test
  void nonDigitCharacters_areRejected() {
    assertThat(PeselValidator.isValid("4405140135A")).isFalse();
  }

  @Test
  void blankString_isRejected() {
    assertThat(PeselValidator.isValid("")).isFalse();
    assertThat(PeselValidator.isValid("   ")).isFalse();
  }
}

package com.dentalclinic.patient.record;

/**
 * Poland's standard 11-digit PESEL format + weighted-checksum validation (research.md #1, FR-002) —
 * server-side, authoritative (the frontend mirrors this for UX only). {@code null} is valid: PESEL
 * is optional (FR-002/FR-003), and a missing PESEL is not this validator's concern.
 */
public final class PeselValidator {

  private static final int[] WEIGHTS = {1, 3, 7, 9, 1, 3, 7, 9, 1, 3};

  private PeselValidator() {}

  public static boolean isValid(String pesel) {
    if (pesel == null) {
      return true;
    }
    if (pesel.length() != 11 || !pesel.chars().allMatch(Character::isDigit)) {
      return false;
    }

    int weightedSum = 0;
    for (int i = 0; i < WEIGHTS.length; i++) {
      weightedSum += WEIGHTS[i] * Character.digit(pesel.charAt(i), 10);
    }
    int expectedCheckDigit = (10 - (weightedSum % 10)) % 10;
    int actualCheckDigit = Character.digit(pesel.charAt(10), 10);

    return expectedCheckDigit == actualCheckDigit;
  }
}

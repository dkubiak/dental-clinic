package com.dentalclinic.patient.toothchart;

/** FR-022/FR-023/FR-011a/FR-036/FR-067 — a finding-creation validation rule was violated. Mapped
 * to 400. */
public class InvalidFindingException extends RuntimeException {
  public InvalidFindingException(String message) {
    super(message);
  }
}

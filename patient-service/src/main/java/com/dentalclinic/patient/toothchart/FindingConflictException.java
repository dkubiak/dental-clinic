package com.dentalclinic.patient.toothchart;

/**
 * FR-040 — the target position/finding is in a state that conflicts with the requested write (e.g.
 * a SURFACE-scope finding on a missing tooth, or correcting an already-superseded finding). Mapped
 * to 409.
 */
public class FindingConflictException extends RuntimeException {
  public FindingConflictException(String message) {
    super(message);
  }
}

package com.dentalclinic.auth.role;

/**
 * The staff roles (FR-003; FR-006a of 002-patient-records added ASSISTANT). Role is the sole
 * permission boundary — see contracts/rbac-policy.md for the full permission matrix (FR-004).
 */
public enum Role {
  RECEPTION,
  DOCTOR,
  ADMINISTRATOR,
  ASSISTANT
}

package com.dentalclinic.auth.role;

/**
 * The three staff roles (FR-003). Role is the sole permission boundary — see
 * contracts/rbac-policy.md for the full permission matrix (FR-004).
 */
public enum Role {
  RECEPTION,
  DOCTOR,
  ADMINISTRATOR
}

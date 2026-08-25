package com.dentalclinic.patient.session;

/**
 * Intentionally duplicated from {@code auth-service}'s {@code com.dentalclinic.auth.role.Role}
 * (plan.md — no shared library exists between the two services yet). Values must stay in sync with
 * that enum and the {@code staff_role} Postgres enum it mirrors; deserialized out of the shared
 * Spring Session attribute {@link SessionAuthenticationFilter} reads (research.md #7).
 */
public enum StaffRole {
  RECEPTION,
  DOCTOR,
  ADMINISTRATOR,
  ASSISTANT
}

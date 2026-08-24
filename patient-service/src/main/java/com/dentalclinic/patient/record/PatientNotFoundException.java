package com.dentalclinic.patient.record;

/**
 * Mapped to 404 by GlobalExceptionHandler — same body as an RBAC denial (rbac-policy.md rule 2: a
 * denial must never reveal whether the target resource exists).
 */
public class PatientNotFoundException extends RuntimeException {}

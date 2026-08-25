package com.dentalclinic.patient.record;

/**
 * FR-003 — the given PESEL already exists on another record (US1 Acceptance Scenario 4). Mapped to
 * 409.
 */
public class DuplicatePeselException extends RuntimeException {}

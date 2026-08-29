package com.dentalclinic.auth.auditlog;

/**
 * Mirrors the `audit_event_type` Postgres enum (V5__audit_log.sql). Covers every FR-006/007/
 * 009a/010/011/017 and User Story 2/3 audit requirement (data-model.md).
 */
public enum AuditEventType {
  LOGIN_SUCCESS,
  LOGIN_FAILURE,
  LOGIN_DENIED_LOCKED,
  LOGIN_DENIED_DEACTIVATED,
  LOGIN_DENIED_RATE_LIMITED,
  MFA_FAILURE,
  MFA_RESET,
  ROLE_CHANGED,
  ACCOUNT_CREATED,
  ACCOUNT_DEACTIVATED,
  ACCOUNT_DEACTIVATION_DENIED_LAST_ADMIN,
  ACCOUNT_REACTIVATED,
  PASSWORD_RESET_REQUESTED,
  PASSWORD_RESET_SUCCEEDED,
  PASSWORD_RESET_FAILED,
  PASSWORD_RESET_EXPIRED,
  ACCESS_DENIED_OUT_OF_ROLE,

  // 002-patient-records (data-model.md AuditLogEntry extension) — written by patient-service via
  // its own PatientAuditWriter, sharing this single hash-chained table (research.md #5).
  PATIENT_RECORD_CREATED,
  PATIENT_RECORD_UPDATED,
  PATIENT_RECORD_VIEWED,
  TOOTH_STATE_CHANGED,
  TOOTH_CHART_VIEWED,
  PATIENT_DATA_EXPORTED,
  PATIENT_DATA_ERASURE_REQUESTED,
  PATIENT_DATA_ERASURE_COMPLETED,

  // 004-patient-medical-history (data-model.md AuditLogEntry extension) — written by
  // patient-service via its own PatientAuditWriter, sharing this single hash-chained table
  // (research.md #2 of 004). Missing this mirror update (while V13__audit_event_type_medical_
  // history.sql and patient-service's own PatientAuditEventType.java WERE updated) broke every
  // subsequent write this service makes once a MEDICAL_HISTORY_* row became the hash chain's
  // tail: reading that row back via JPA to continue the chain threw IllegalArgumentException
  // ("No enum constant ...MEDICAL_HISTORY_ENTRY_VIEWED"), surfacing as an opaque failure on
  // unrelated endpoints (e.g. POST /auth/mfa/verify) — caught via real end-to-end Playwright
  // testing against the full docker-compose stack, not by any single-service test suite.
  MEDICAL_HISTORY_ENTRY_ADDED,
  MEDICAL_HISTORY_ENTRY_VIEWED,
  MEDICAL_HISTORY_HISTORY_VIEWED
}

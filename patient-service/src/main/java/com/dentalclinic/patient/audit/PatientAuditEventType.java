package com.dentalclinic.patient.audit;

/**
 * The subset of the shared {@code audit_event_type} Postgres enum
 * (backend/.../V11__audit_event_type_patient.sql) that {@code patient-service} itself ever writes.
 * Intentionally duplicated/scoped-down from {@code auth-service}'s own {@code AuditEventType} — no
 * shared library exists between the two services yet (plan.md), and this service has no reason to
 * reference auth-service's own login/account event types.
 */
public enum PatientAuditEventType {
  PATIENT_RECORD_CREATED,
  PATIENT_RECORD_UPDATED,
  PATIENT_RECORD_VIEWED,
  TOOTH_STATE_CHANGED,
  TOOTH_CHART_VIEWED,
  PATIENT_DATA_EXPORTED,
  PATIENT_DATA_ERASURE_REQUESTED,
  PATIENT_DATA_ERASURE_COMPLETED,
  MEDICAL_HISTORY_ENTRY_ADDED,
  MEDICAL_HISTORY_ENTRY_VIEWED,
  MEDICAL_HISTORY_HISTORY_VIEWED,
  TOOTH_POSITION_PRESENCE_CHANGED,
  DENTITION_MODE_CHANGED,
  ROOT_CANAL_ADDED,
  ROOT_CANAL_CHANGED,
  ROOT_CANAL_REMOVED,
  TOOTH_FINDING_ADDED
}

package com.dentalclinic.patient.medicalhistory;

/**
 * FR-003 — the patient's actual clinical state (active/past), independent of {@link RecordStatus}
 * (Clarifications Session 2026-08-29 Q1, data-model.md ChronicConditionEntry).
 */
public enum ChronicConditionStatus {
  ACTIVE,
  PAST
}

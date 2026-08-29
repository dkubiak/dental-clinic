package com.dentalclinic.patient.medicalhistory;

/**
 * Technical correction-lifecycle flag shared by all three medical-history entities (data-model.md),
 * independent of any clinical-status field an entity may also carry (see {@link
 * ChronicConditionStatus}). {@code CURRENT} → {@code SUPERSEDED} is the only transition, applied as
 * a side effect of inserting a new {@code CURRENT} row that supersedes this one (FR-010).
 */
public enum RecordStatus {
  CURRENT,
  SUPERSEDED
}

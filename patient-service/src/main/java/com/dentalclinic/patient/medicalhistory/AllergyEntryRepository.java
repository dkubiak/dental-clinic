package com.dentalclinic.patient.medicalhistory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AllergyEntryRepository extends JpaRepository<AllergyEntry, UUID> {

  /** FR-001/SC-004 — default view, current entries only. */
  List<AllergyEntry> findByPatientRecordIdAndRecordStatusOrderByCreatedAtDesc(
      UUID patientRecordId, RecordStatus recordStatus);

  /** FR-010 — "historia zmian", current and superseded together. */
  List<AllergyEntry> findByPatientRecordIdOrderByCreatedAtDesc(UUID patientRecordId);

  /** FR-006 — computed hasCriticalAllergyAlert (data-model.md). */
  boolean existsByPatientRecordIdAndRecordStatusAndSeverity(
      UUID patientRecordId, RecordStatus recordStatus, AllergySeverity severity);
}

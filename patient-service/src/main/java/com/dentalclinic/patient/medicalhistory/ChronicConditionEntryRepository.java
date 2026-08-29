package com.dentalclinic.patient.medicalhistory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChronicConditionEntryRepository
    extends JpaRepository<ChronicConditionEntry, UUID> {

  /** FR-003 — default view, current entries only. */
  List<ChronicConditionEntry> findByPatientRecordIdAndRecordStatusOrderByCreatedAtDesc(
      UUID patientRecordId, RecordStatus recordStatus);

  /** FR-010 — "historia zmian", current and superseded together. */
  List<ChronicConditionEntry> findByPatientRecordIdOrderByCreatedAtDesc(UUID patientRecordId);
}

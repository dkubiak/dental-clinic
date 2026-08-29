package com.dentalclinic.patient.medicalhistory;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MedicationEntryRepository extends JpaRepository<MedicationEntry, UUID> {

  /** FR-002 — default view, current entries only. */
  List<MedicationEntry> findByPatientRecordIdAndRecordStatusOrderByCreatedAtDesc(
      UUID patientRecordId, RecordStatus recordStatus);

  /** FR-010 — "historia zmian", current and superseded together. */
  List<MedicationEntry> findByPatientRecordIdOrderByCreatedAtDesc(UUID patientRecordId);
}

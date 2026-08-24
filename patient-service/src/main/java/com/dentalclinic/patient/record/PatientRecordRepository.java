package com.dentalclinic.patient.record;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PatientRecordRepository extends JpaRepository<PatientRecord, UUID> {

  /** FR-003 — duplicate-PESEL check ahead of the DB's own partial unique index. */
  Optional<PatientRecord> findByPesel(String pesel);

  /** FR-012 — case-insensitive, partial last-name match (uses `idx_patient_record_last_name`). */
  List<PatientRecord> findByLastNameIgnoreCaseContaining(String lastNameFragment);
}

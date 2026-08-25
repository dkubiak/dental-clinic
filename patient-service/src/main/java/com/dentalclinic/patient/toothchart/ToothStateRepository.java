package com.dentalclinic.patient.toothchart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToothStateRepository extends JpaRepository<ToothState, UUID> {

  /** FR-005 — full chart read, stable tooth order. */
  List<ToothState> findByPatientRecordIdOrderByToothNumberAsc(UUID patientRecordId);

  /** FR-006 — single-tooth lookup for a status update. */
  Optional<ToothState> findByPatientRecordIdAndToothNumber(UUID patientRecordId, int toothNumber);
}

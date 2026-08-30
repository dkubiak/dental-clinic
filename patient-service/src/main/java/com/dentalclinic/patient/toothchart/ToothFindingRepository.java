package com.dentalclinic.patient.toothchart;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToothFindingRepository extends JpaRepository<ToothFinding, UUID> {

  List<ToothFinding> findByToothPositionIdAndRecordStatus(
      UUID toothPositionId, FindingRecordStatus recordStatus);

  /** FR-034 — full per-position history: current, resolved, and superseded alike. */
  List<ToothFinding> findByToothPositionIdOrderByCreatedAtAsc(UUID toothPositionId);

  List<ToothFinding> findByToothPositionIdInAndRecordStatus(
      List<UUID> toothPositionIds, FindingRecordStatus recordStatus);
}

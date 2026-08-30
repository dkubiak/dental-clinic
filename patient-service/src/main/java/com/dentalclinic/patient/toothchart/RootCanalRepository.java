package com.dentalclinic.patient.toothchart;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RootCanalRepository extends JpaRepository<RootCanal, UUID> {

  List<RootCanal> findByToothPositionIdAndRemovedFalse(UUID toothPositionId);

  List<RootCanal> findByToothPositionId(UUID toothPositionId);
}

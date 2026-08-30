package com.dentalclinic.patient.toothchart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToothPositionRepository extends JpaRepository<ToothPosition, UUID> {

  List<ToothPosition> findByToothChartIdOrderByFdiNumberAsc(UUID toothChartId);

  Optional<ToothPosition> findByToothChartIdAndFdiNumber(UUID toothChartId, int fdiNumber);
}

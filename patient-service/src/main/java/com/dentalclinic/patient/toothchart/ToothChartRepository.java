package com.dentalclinic.patient.toothchart;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToothChartRepository extends JpaRepository<ToothChart, UUID> {

  Optional<ToothChart> findByPatientRecordId(UUID patientRecordId);
}

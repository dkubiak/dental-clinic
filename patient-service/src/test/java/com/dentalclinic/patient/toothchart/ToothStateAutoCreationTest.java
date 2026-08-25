package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T041 — creating a patient yields exactly 32 {@code HEALTHY} rows, one per valid FDI tooth number
 * (data-model.md ToothState, US2 Acceptance Scenario 3).
 */
class ToothStateAutoCreationTest extends PostgresIntegrationTestBase {

  @Autowired private PatientCreateService patientCreateService;
  @Autowired private ToothStateRepository toothStateRepository;

  @Test
  void creatingPatient_yieldsThirtyTwoHealthyTeeth_onePerFdiToothNumber() {
    PatientRecord record =
        patientCreateService.create(
            "Jan",
            "ToothTest",
            LocalDate.of(1990, 1, 1),
            null,
            "Polna",
            "1",
            "00-001",
            "Warszawa",
            UUID.randomUUID());

    var teeth = toothStateRepository.findByPatientRecordIdOrderByToothNumberAsc(record.getId());

    assertThat(teeth).hasSize(32);
    assertThat(teeth).allMatch(tooth -> tooth.getStatus() == ToothStatus.HEALTHY);

    Set<Integer> actualNumbers =
        teeth.stream().map(ToothState::getToothNumber).collect(Collectors.toSet());
    assertThat(actualNumbers).isEqualTo(expectedFdiToothNumbers());
  }

  private static Set<Integer> expectedFdiToothNumbers() {
    Set<Integer> numbers = new HashSet<>();
    for (int quadrant = 1; quadrant <= 4; quadrant++) {
      for (int position = 1; position <= 8; position++) {
        numbers.add(quadrant * 10 + position);
      }
    }
    return numbers;
  }
}

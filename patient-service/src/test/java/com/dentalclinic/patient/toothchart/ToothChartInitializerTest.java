package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientRecord;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T028 — {@link ToothChartInitializer} creates one {@link ToothChart} row and all 52 {@link
 * ToothPosition} rows (32 permanent FDI 11-48 + 20 deciduous FDI 51-85, correct
 * dentitionType/toothType per position) regardless of dentitionMode (research.md D2).
 */
class ToothChartInitializerTest extends PostgresIntegrationTestBase {

  @Autowired private ToothChartInitializer initializer;
  @Autowired private ToothChartRepository toothChartRepository;
  @Autowired private ToothPositionRepository toothPositionRepository;
  @Autowired private PatientRecordRepository patientRecordRepository;

  @Test
  void initialize_createsOneChartAndFiftyTwoPositions_withCorrectTypesPerFdiNumber() {
    PatientRecord patient =
        new PatientRecord(
            UUID.randomUUID(),
            "Jan",
            "Initializer",
            LocalDate.of(1990, 1, 1),
            null,
            "Polna",
            "1",
            "00-001",
            "Warszawa",
            UUID.randomUUID());
    patientRecordRepository.save(patient);
    UUID patientRecordId = patient.getId();

    initializer.initialize(patientRecordId, DentitionMode.PERMANENT);

    ToothChart chart = toothChartRepository.findByPatientRecordId(patientRecordId).orElseThrow();
    var positions = toothPositionRepository.findByToothChartIdOrderByFdiNumberAsc(chart.getId());

    assertThat(positions).hasSize(52);

    Set<Integer> expectedPermanent =
        expand(new int[] {1, 2, 3, 4}, new int[] {1, 2, 3, 4, 5, 6, 7, 8});
    Set<Integer> expectedDeciduous =
        expand(new int[] {5, 6, 7, 8}, new int[] {1, 2, 3, 4, 5});
    Set<Integer> actual =
        positions.stream().map(ToothPosition::getFdiNumber).collect(Collectors.toSet());
    assertThat(actual).containsExactlyInAnyOrderElementsOf(
        Stream.concat(expectedPermanent.stream(), expectedDeciduous.stream())
            .collect(Collectors.toSet()));

    Map<Integer, ToothPosition> byFdi =
        positions.stream().collect(Collectors.toMap(ToothPosition::getFdiNumber, p -> p));

    assertThat(byFdi.get(11).getDentitionType()).isEqualTo(DentitionType.PERMANENT);
    assertThat(byFdi.get(11).getToothType()).isEqualTo(ToothType.INCISOR);
    assertThat(byFdi.get(13).getToothType()).isEqualTo(ToothType.CANINE);
    assertThat(byFdi.get(14).getToothType()).isEqualTo(ToothType.PREMOLAR);
    assertThat(byFdi.get(16).getToothType()).isEqualTo(ToothType.MOLAR);
    assertThat(byFdi.get(18).getToothType()).isEqualTo(ToothType.MOLAR);

    assertThat(byFdi.get(51).getDentitionType()).isEqualTo(DentitionType.DECIDUOUS);
    assertThat(byFdi.get(51).getToothType()).isEqualTo(ToothType.INCISOR);
    assertThat(byFdi.get(53).getToothType()).isEqualTo(ToothType.CANINE);
    assertThat(byFdi.get(54).getToothType()).isEqualTo(ToothType.MOLAR);
    assertThat(byFdi.get(55).getToothType()).isEqualTo(ToothType.MOLAR);

    assertThat(positions).allMatch(p -> p.getPresence() == ToothPresence.PRESENT);
  }

  private static Set<Integer> expand(int[] quadrants, int[] positions) {
    Set<Integer> numbers = new HashSet<>();
    for (int quadrant : quadrants) {
      for (int position : positions) {
        numbers.add(quadrant * 10 + position);
      }
    }
    return numbers;
  }
}

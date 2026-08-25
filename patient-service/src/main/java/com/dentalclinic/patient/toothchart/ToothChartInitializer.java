package com.dentalclinic.patient.toothchart;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * FR-005/research.md #3 — creates all 32 adult permanent-dentition {@link ToothState} rows (FDI/ISO
 * 3950 numbering: 11–18, 21–28, 31–38, 41–48) at PatientRecord creation time, invoked from {@code
 * PatientCreateService} — "new record ⇒ all teeth healthy" needs no null-handling.
 */
@Component
public class ToothChartInitializer {

  static final List<Integer> FDI_TOOTH_NUMBERS = buildFdiToothNumbers();

  private final ToothStateRepository repository;

  public ToothChartInitializer(ToothStateRepository repository) {
    this.repository = repository;
  }

  public void initialize(UUID patientRecordId) {
    List<ToothState> teeth = new ArrayList<>(FDI_TOOTH_NUMBERS.size());
    for (int toothNumber : FDI_TOOTH_NUMBERS) {
      teeth.add(new ToothState(UUID.randomUUID(), patientRecordId, toothNumber));
    }
    repository.saveAll(teeth);
  }

  private static List<Integer> buildFdiToothNumbers() {
    List<Integer> numbers = new ArrayList<>(32);
    for (int quadrant = 1; quadrant <= 4; quadrant++) {
      for (int position = 1; position <= 8; position++) {
        numbers.add(quadrant * 10 + position);
      }
    }
    return List.copyOf(numbers);
  }
}

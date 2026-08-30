package com.dentalclinic.patient.toothchart;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * FR-005/research.md D2 — creates the {@link ToothChart} row plus all 52 {@link ToothPosition} rows
 * (32 permanent FDI 11-48 + 20 deciduous FDI 51-85) at patient-record creation time, regardless of
 * {@code dentitionMode} — the mode only ever controls which positions the frontend renders, never
 * which rows exist (FR-047).
 */
@Component
public class ToothChartInitializer {

  private final ToothChartRepository toothChartRepository;
  private final ToothPositionRepository toothPositionRepository;

  public ToothChartInitializer(
      ToothChartRepository toothChartRepository, ToothPositionRepository toothPositionRepository) {
    this.toothChartRepository = toothChartRepository;
    this.toothPositionRepository = toothPositionRepository;
  }

  /**
   * FR-044 — {@code dentitionMode} defaults from age at creation time: DECIDUOUS &lt;6y, MIXED
   * 6-13y, PERMANENT otherwise.
   */
  public void initialize(UUID patientRecordId, LocalDate dateOfBirth) {
    initialize(patientRecordId, defaultDentitionMode(dateOfBirth));
  }

  public void initialize(UUID patientRecordId, DentitionMode dentitionMode) {
    ToothChart chart = new ToothChart(UUID.randomUUID(), patientRecordId, dentitionMode);
    toothChartRepository.save(chart);

    List<ToothPosition> positions = new ArrayList<>(52);
    for (int quadrant = 1; quadrant <= 4; quadrant++) {
      for (int position = 1; position <= 8; position++) {
        int fdiNumber = quadrant * 10 + position;
        positions.add(
            new ToothPosition(
                UUID.randomUUID(),
                chart.getId(),
                fdiNumber,
                DentitionType.PERMANENT,
                toothType(position, false)));
      }
    }
    for (int quadrant = 5; quadrant <= 8; quadrant++) {
      for (int position = 1; position <= 5; position++) {
        int fdiNumber = quadrant * 10 + position;
        positions.add(
            new ToothPosition(
                UUID.randomUUID(),
                chart.getId(),
                fdiNumber,
                DentitionType.DECIDUOUS,
                toothType(position, true)));
      }
    }
    toothPositionRepository.saveAll(positions);
  }

  static DentitionMode defaultDentitionMode(LocalDate dateOfBirth) {
    int ageYears = Period.between(dateOfBirth, LocalDate.now()).getYears();
    if (ageYears < 6) {
      return DentitionMode.DECIDUOUS;
    }
    if (ageYears <= 13) {
      return DentitionMode.MIXED;
    }
    return DentitionMode.PERMANENT;
  }

  private static ToothType toothType(int position, boolean deciduous) {
    if (position <= 2) {
      return ToothType.INCISOR;
    }
    if (position == 3) {
      return ToothType.CANINE;
    }
    if (deciduous) {
      return ToothType.MOLAR; // deciduous quadrants only go to position 5, no premolars
    }
    return position <= 5 ? ToothType.PREMOLAR : ToothType.MOLAR;
  }
}

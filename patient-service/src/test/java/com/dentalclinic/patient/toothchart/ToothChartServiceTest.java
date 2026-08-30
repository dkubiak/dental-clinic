package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T030 — {@link ToothChartService#getChart} returns 32 PERMANENT positions, all PRESENT, zero
 * findings, for a fresh adult patient (US1 Acceptance Scenario 1).
 */
class ToothChartServiceTest extends PostgresIntegrationTestBase {

  @Autowired private PatientCreateService patientCreateService;
  @Autowired private ToothChartService toothChartService;
  @Autowired private ToothFindingService toothFindingService;
  @Autowired private DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository;

  private PatientRecord createAdultPatient(String pesel) {
    return createPatient(pesel, LocalDate.of(1990, 1, 1));
  }

  private PatientRecord createPatient(String pesel, LocalDate dateOfBirth) {
    return patientCreateService.create(
        "Jan",
        "Odontogram",
        dateOfBirth,
        pesel,
        "Polna",
        "1",
        "00-001",
        "Warszawa",
        UUID.randomUUID());
  }

  @Test
  void getChart_returnsThirtyTwoPresentPermanentPositions_withNoFindings_forFreshAdultPatient() {
    PatientRecord patient = createAdultPatient("90011502503");

    ToothChartService.ChartView view =
        toothChartService.getChart(patient.getId(), UUID.randomUUID());

    assertThat(view.chart().getDentitionMode()).isEqualTo(DentitionMode.PERMANENT);
    assertThat(view.positions()).hasSize(52);

    long permanentCount =
        view.positions().stream().filter(p -> p.dentitionType() == DentitionType.PERMANENT).count();
    assertThat(permanentCount).isEqualTo(32);
    assertThat(view.positions()).allMatch(p -> p.presence() == ToothPresence.PRESENT);
    assertThat(view.positions()).allMatch(p -> p.currentFindings().isEmpty());
  }

  /**
   * T090 — presence PATCH with a stale expectedVersion fails (FR-070); setting EXTRACTED blocks a
   * new SURFACE-scope finding (FR-040) but allows an allowedForMissingTooth entry (FR-041).
   */
  @Test
  void changePresence_rejectsStaleExpectedVersion() {
    PatientRecord patient = createAdultPatient("90011530005");

    toothChartService.changePresence(
        patient.getId(),
        36,
        ToothPresence.EXTRACTED,
        LocalDate.of(2026, 8, 30),
        0,
        UUID.randomUUID());

    assertThatThrownBy(
            () ->
                toothChartService.changePresence(
                    patient.getId(), 36, ToothPresence.PRESENT, null, 0, UUID.randomUUID()))
        .isInstanceOf(FindingConflictException.class);
  }

  @Test
  void changePresence_toExtracted_blocksSurfaceScopeFinding_butAllowsAllowedForMissingToothEntry() {
    PatientRecord patient = createAdultPatient("90011530012");
    DiagnosisCatalogEntry caries =
        diagnosisCatalogEntryRepository.findByCode("K02.1").orElseThrow();
    DiagnosisCatalogEntry implant =
        diagnosisCatalogEntryRepository.findByCode("IMPL").orElseThrow();

    toothChartService.changePresence(
        patient.getId(),
        36,
        ToothPresence.EXTRACTED,
        LocalDate.of(2026, 8, 30),
        0,
        UUID.randomUUID());

    assertThatThrownBy(
            () ->
                toothFindingService.addFinding(
                    patient.getId(),
                    36,
                    caries.getId(),
                    List.of(ToothSurface.MESIAL),
                    null,
                    null,
                    null,
                    null,
                    LocalDate.of(2026, 8, 30),
                    UUID.randomUUID(),
                    FindingAuthorRole.DOCTOR))
        .isInstanceOf(FindingConflictException.class);

    ToothFinding implantFinding =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            implant.getId(),
            null,
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);
    assertThat(implantFinding.getClinicalStatus()).isEqualTo(FindingClinicalStatus.ACTIVE);
  }

  /**
   * T104 — dentitionMode defaults from age (DECIDUOUS &lt;6y, MIXED 6-13y, PERMANENT else, FR-044);
   * changing mode never deletes/modifies any ToothPosition/ToothFinding row (FR-047).
   */
  @Test
  void defaultDentitionMode_isDeciduousUnderSix_mixedBetweenSixAndThirteen_permanentOtherwise() {
    assertThat(ToothChartInitializer.defaultDentitionMode(LocalDate.now().minusYears(4)))
        .isEqualTo(DentitionMode.DECIDUOUS);
    assertThat(ToothChartInitializer.defaultDentitionMode(LocalDate.now().minusYears(9)))
        .isEqualTo(DentitionMode.MIXED);
    assertThat(ToothChartInitializer.defaultDentitionMode(LocalDate.now().minusYears(30)))
        .isEqualTo(DentitionMode.PERMANENT);
  }

  @Test
  void changeDentitionMode_neverDeletesOrModifiesAnyPositionOrFinding() {
    PatientRecord patient = createAdultPatient("90011540046");
    DiagnosisCatalogEntry caries =
        diagnosisCatalogEntryRepository.findByCode("K02.1").orElseThrow();
    ToothFinding finding =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            caries.getId(),
            List.of(ToothSurface.MESIAL),
            null,
            null,
            null,
            null,
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);
    ToothChartService.ChartView before =
        toothChartService.getChart(patient.getId(), UUID.randomUUID());

    ToothChartService.ChartView after =
        toothChartService.changeDentitionMode(
            patient.getId(), DentitionMode.MIXED, UUID.randomUUID());

    assertThat(after.chart().getDentitionMode()).isEqualTo(DentitionMode.MIXED);
    assertThat(after.positions()).hasSize(before.positions().size()).hasSize(52);
    boolean stillPresent =
        after.positions().stream()
            .filter(p -> p.fdiNumber() == 36)
            .flatMap(p -> p.currentFindings().stream())
            .anyMatch(fv -> fv.finding().getId().equals(finding.getId()));
    assertThat(stillPresent).isTrue();
  }
}

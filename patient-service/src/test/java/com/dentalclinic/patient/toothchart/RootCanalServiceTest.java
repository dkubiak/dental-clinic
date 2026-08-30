package com.dentalclinic.patient.toothchart;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T091 — {@link RootCanalService} enforces max 6 non-removed canals per position and requires
 * {@code presence = PRESENT} to add one; rename/state-change with a stale {@code expectedVersion}
 * fails; soft-removing a canal never deletes or hides findings referencing it (FR-068).
 */
class RootCanalServiceTest extends PostgresIntegrationTestBase {

  @Autowired private PatientCreateService patientCreateService;
  @Autowired private RootCanalService rootCanalService;
  @Autowired private ToothChartService toothChartService;
  @Autowired private ToothFindingService toothFindingService;
  @Autowired private DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository;
  @Autowired private ToothFindingRepository toothFindingRepository;

  private PatientRecord createAdultPatient(String pesel) {
    return patientCreateService.create(
        "Jan",
        "Canals",
        LocalDate.of(1990, 1, 1),
        pesel,
        "Polna",
        "1",
        "00-001",
        "Warszawa",
        UUID.randomUUID());
  }

  @Test
  void addCanal_requiresPresenceToBePresent() {
    PatientRecord patient = createAdultPatient("90011531006");
    toothChartService.changePresence(
        patient.getId(),
        36,
        ToothPresence.EXTRACTED,
        LocalDate.of(2026, 8, 30),
        0,
        UUID.randomUUID());

    assertThatThrownBy(
            () -> rootCanalService.addCanal(patient.getId(), 36, "MB", UUID.randomUUID()))
        .isInstanceOf(FindingConflictException.class);
  }

  @Test
  void addCanal_enforcesMaxSixNonRemovedCanals() {
    PatientRecord patient = createAdultPatient("90011531013");
    for (int i = 0; i < 6; i++) {
      rootCanalService.addCanal(patient.getId(), 36, "Kanał " + i, UUID.randomUUID());
    }

    assertThatThrownBy(
            () -> rootCanalService.addCanal(patient.getId(), 36, "Kanał 7", UUID.randomUUID()))
        .isInstanceOf(FindingConflictException.class);
  }

  @Test
  void updateCanal_rejectsStaleExpectedVersion() {
    PatientRecord patient = createAdultPatient("90011531020");
    RootCanal canal = rootCanalService.addCanal(patient.getId(), 36, "MB", UUID.randomUUID());

    rootCanalService.updateCanal(
        patient.getId(), 36, canal.getId(), null, RootCanalState.TREATED, 0, UUID.randomUUID());

    assertThatThrownBy(
            () ->
                rootCanalService.updateCanal(
                    patient.getId(), 36, canal.getId(), "MB2", null, 0, UUID.randomUUID()))
        .isInstanceOf(FindingConflictException.class);
  }

  @Test
  void removeCanal_softDeletesOnly_andFindingsReferencingItAreUnaffected() {
    PatientRecord patient = createAdultPatient("90011531037");
    RootCanal canal = rootCanalService.addCanal(patient.getId(), 36, "MB", UUID.randomUUID());
    DiagnosisCatalogEntry endo =
        diagnosisCatalogEntryRepository.findByCode("K04.7").orElseThrow(); // ROOT_PERIAPICAL scope
    ToothFinding finding =
        toothFindingService.addFinding(
            patient.getId(),
            36,
            endo.getId(),
            null,
            canal.getId(),
            null,
            null,
            null,
            LocalDate.of(2026, 8, 30),
            UUID.randomUUID(),
            FindingAuthorRole.DOCTOR);

    rootCanalService.removeCanal(patient.getId(), 36, canal.getId(), UUID.randomUUID());

    ToothFinding reloaded = toothFindingRepository.findById(finding.getId()).orElseThrow();
    assertThat(reloaded.getRootCanalId()).isEqualTo(canal.getId());
    assertThat(reloaded.getRecordStatus()).isEqualTo(FindingRecordStatus.CURRENT);
  }
}

package com.dentalclinic.patient.medicalhistory;

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
 * T036 — {@code clinicalStatus} (ACTIVE/PAST) and {@code recordStatus} (CURRENT/SUPERSEDED) are
 * independent state machines on the same entity (Clarifications Session 2026-08-29 Q1,
 * data-model.md); plus free-text/non-blank validation (FR-011), mirrors {@code
 * AllergyEntryServiceTest}.
 */
class ChronicConditionEntryServiceTest extends PostgresIntegrationTestBase {

  @Autowired private MedicalHistoryService medicalHistoryService;
  @Autowired private PatientCreateService patientCreateService;
  @Autowired private ChronicConditionEntryRepository chronicConditionEntryRepository;

  private UUID createPatient() {
    PatientRecord record =
        patientCreateService.create(
            "Jan",
            "Przewlekly",
            LocalDate.of(1990, 1, 1),
            null,
            "Polna",
            "1",
            "00-001",
            "Warszawa",
            UUID.randomUUID());
    return record.getId();
  }

  @Test
  void correction_canFlipClinicalStatus_whileRecordStatusFollowsTheAppendOnlyRule() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    ChronicConditionEntry original =
        medicalHistoryService.addChronicCondition(
            patientId,
            "Cukrzyca typu 2",
            ChronicConditionStatus.ACTIVE,
            LocalDate.of(2020, 3, 15),
            null,
            actorId);

    ChronicConditionEntry correction =
        medicalHistoryService.addChronicCondition(
            patientId,
            "Cukrzyca typu 2",
            ChronicConditionStatus.PAST,
            LocalDate.of(2020, 3, 15),
            original.getId(),
            actorId);

    ChronicConditionEntry reloadedOriginal =
        chronicConditionEntryRepository.findById(original.getId()).orElseThrow();
    assertThat(reloadedOriginal.getRecordStatus()).isEqualTo(RecordStatus.SUPERSEDED);
    assertThat(reloadedOriginal.getClinicalStatus()).isEqualTo(ChronicConditionStatus.ACTIVE);
    assertThat(correction.getRecordStatus()).isEqualTo(RecordStatus.CURRENT);
    assertThat(correction.getClinicalStatus()).isEqualTo(ChronicConditionStatus.PAST);
  }

  @Test
  void correction_canLeaveClinicalStatusUnchanged_whileStillFollowingAppendOnlyRule() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    ChronicConditionEntry original =
        medicalHistoryService.addChronicCondition(
            patientId,
            "Nadciśnienie",
            ChronicConditionStatus.ACTIVE,
            LocalDate.of(2021, 6, 1),
            null,
            actorId);

    ChronicConditionEntry correction =
        medicalHistoryService.addChronicCondition(
            patientId,
            "Nadciśnienie tętnicze",
            ChronicConditionStatus.ACTIVE,
            LocalDate.of(2021, 6, 1),
            original.getId(),
            actorId);

    assertThat(correction.getClinicalStatus()).isEqualTo(ChronicConditionStatus.ACTIVE);
    assertThat(correction.getName()).isEqualTo("Nadciśnienie tętnicze");
  }

  @Test
  void getCurrentChronicConditions_excludesSupersededEntries_whileHistoryIncludesBoth() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    ChronicConditionEntry original =
        medicalHistoryService.addChronicCondition(
            patientId,
            "Astma",
            ChronicConditionStatus.ACTIVE,
            LocalDate.of(2019, 1, 1),
            null,
            actorId);
    medicalHistoryService.addChronicCondition(
        patientId,
        "Astma",
        ChronicConditionStatus.PAST,
        LocalDate.of(2019, 1, 1),
        original.getId(),
        actorId);

    assertThat(medicalHistoryService.getCurrentChronicConditions(patientId, actorId))
        .extracting(ChronicConditionEntry::getRecordStatus)
        .containsOnly(RecordStatus.CURRENT);
    assertThat(medicalHistoryService.getChronicConditionHistory(patientId, actorId)).hasSize(2);
  }

  @Test
  void blankName_isRejected() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                medicalHistoryService.addChronicCondition(
                    patientId,
                    "  ",
                    ChronicConditionStatus.ACTIVE,
                    LocalDate.of(2020, 1, 1),
                    null,
                    actorId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arbitraryFreeTextName_isAcceptedWithoutDictionaryValidation() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    ChronicConditionEntry entry =
        medicalHistoryService.addChronicCondition(
            patientId,
            "Choroba spoza ICD-10, opis własny lekarza",
            ChronicConditionStatus.ACTIVE,
            LocalDate.of(2020, 1, 1),
            null,
            actorId);

    assertThat(entry.getName()).isEqualTo("Choroba spoza ICD-10, opis własny lekarza");
  }
}

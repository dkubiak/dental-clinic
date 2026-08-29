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
 * T005 — append-only correction behavior (FR-010) and free-text/non-blank validation (FR-011) for
 * {@code AllergyEntry}, mirrors {@code ToothStateAutoCreationTest}'s use of the real
 * PatientCreateService against a Testcontainers-backed Postgres.
 */
class AllergyEntryServiceTest extends PostgresIntegrationTestBase {

  @Autowired private MedicalHistoryService medicalHistoryService;
  @Autowired private PatientCreateService patientCreateService;
  @Autowired private AllergyEntryRepository allergyEntryRepository;

  private UUID createPatient() {
    PatientRecord record =
        patientCreateService.create(
            "Jan",
            "Alergiczny",
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
  void addingACorrection_flipsThePriorEntryToSuperseded_inTheSameTransaction() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    AllergyEntry original =
        medicalHistoryService.addAllergy(
            patientId, "Penicylina", "Anafilaksja", AllergySeverity.CRITICAL, null, actorId);

    AllergyEntry correction =
        medicalHistoryService.addAllergy(
            patientId,
            "Penicylina",
            "Anafilaksja",
            AllergySeverity.MODERATE,
            original.getId(),
            actorId);

    AllergyEntry reloadedOriginal = allergyEntryRepository.findById(original.getId()).orElseThrow();
    assertThat(reloadedOriginal.getRecordStatus()).isEqualTo(RecordStatus.SUPERSEDED);
    assertThat(correction.getRecordStatus()).isEqualTo(RecordStatus.CURRENT);
    assertThat(correction.getSupersedesEntryId()).isEqualTo(original.getId());
    assertThat(correction.getSeverity()).isEqualTo(AllergySeverity.MODERATE);

    // No in-place mutation possible — the original row's clinical fields are untouched.
    assertThat(reloadedOriginal.getSeverity()).isEqualTo(AllergySeverity.CRITICAL);
  }

  @Test
  void getCurrentAllergies_excludesSupersededEntries_whileHistoryIncludesBoth() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    AllergyEntry original =
        medicalHistoryService.addAllergy(
            patientId, "Lateks", "Wysypka", AllergySeverity.MODERATE, null, actorId);
    medicalHistoryService.addAllergy(
        patientId, "Lateks", "Wysypka", AllergySeverity.CRITICAL, original.getId(), actorId);

    assertThat(medicalHistoryService.getCurrentAllergies(patientId, actorId))
        .extracting(AllergyEntry::getRecordStatus)
        .containsOnly(RecordStatus.CURRENT);
    assertThat(medicalHistoryService.getAllergyHistory(patientId, actorId)).hasSize(2);
  }

  @Test
  void blankSubstanceOrReactionType_isRejected() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                medicalHistoryService.addAllergy(
                    patientId, "  ", "Anafilaksja", AllergySeverity.CRITICAL, null, actorId))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                medicalHistoryService.addAllergy(
                    patientId, "Penicylina", "", AllergySeverity.CRITICAL, null, actorId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arbitraryFreeTextSubstance_isAcceptedWithoutDictionaryValidation() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    AllergyEntry entry =
        medicalHistoryService.addAllergy(
            patientId,
            "Coś bardzo nietypowego, spoza słownika ICD",
            "Reakcja niestandardowa",
            AllergySeverity.MODERATE,
            null,
            actorId);

    assertThat(entry.getSubstance()).isEqualTo("Coś bardzo nietypowego, spoza słownika ICD");
  }
}

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
 * T023 — append-only correction behavior (FR-010) and free-text/non-blank validation (FR-011) for
 * {@code MedicationEntry}, mirrors {@code AllergyEntryServiceTest}.
 */
class MedicationEntryServiceTest extends PostgresIntegrationTestBase {

  @Autowired private MedicalHistoryService medicalHistoryService;
  @Autowired private PatientCreateService patientCreateService;
  @Autowired private MedicationEntryRepository medicationEntryRepository;

  private UUID createPatient() {
    PatientRecord record =
        patientCreateService.create(
            "Jan",
            "Lekowy",
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

    MedicationEntry original =
        medicalHistoryService.addMedication(
            patientId, "Ibuprofen", "400mg 2x/dzień", LocalDate.of(2026, 1, 1), null, actorId);

    MedicationEntry correction =
        medicalHistoryService.addMedication(
            patientId,
            "Ibuprofen",
            "200mg 2x/dzień",
            LocalDate.of(2026, 1, 1),
            original.getId(),
            actorId);

    MedicationEntry reloadedOriginal =
        medicationEntryRepository.findById(original.getId()).orElseThrow();
    assertThat(reloadedOriginal.getRecordStatus()).isEqualTo(RecordStatus.SUPERSEDED);
    assertThat(correction.getRecordStatus()).isEqualTo(RecordStatus.CURRENT);
    assertThat(correction.getSupersedesEntryId()).isEqualTo(original.getId());
    assertThat(reloadedOriginal.getDosage()).isEqualTo("400mg 2x/dzień");
  }

  @Test
  void getCurrentMedications_excludesSupersededEntries_whileHistoryIncludesBoth() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    MedicationEntry original =
        medicalHistoryService.addMedication(
            patientId, "Paracetamol", "500mg", LocalDate.of(2026, 2, 1), null, actorId);
    medicalHistoryService.addMedication(
        patientId, "Paracetamol", "1g", LocalDate.of(2026, 2, 1), original.getId(), actorId);

    assertThat(medicalHistoryService.getCurrentMedications(patientId, actorId))
        .extracting(MedicationEntry::getRecordStatus)
        .containsOnly(RecordStatus.CURRENT);
    assertThat(medicalHistoryService.getMedicationHistory(patientId, actorId)).hasSize(2);
  }

  @Test
  void blankNameOrDosage_isRejected() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                medicalHistoryService.addMedication(
                    patientId, " ", "400mg", LocalDate.of(2026, 1, 1), null, actorId))
        .isInstanceOf(IllegalArgumentException.class);

    assertThatThrownBy(
            () ->
                medicalHistoryService.addMedication(
                    patientId, "Ibuprofen", "", LocalDate.of(2026, 1, 1), null, actorId))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void arbitraryFreeTextName_isAcceptedWithoutDictionaryValidation() {
    UUID patientId = createPatient();
    UUID actorId = UUID.randomUUID();

    MedicationEntry entry =
        medicalHistoryService.addMedication(
            patientId,
            "Lek spoza rejestru, nazwa własna apteki",
            "wg zalecenia",
            LocalDate.of(2026, 1, 1),
            null,
            actorId);

    assertThat(entry.getName()).isEqualTo("Lek spoza rejestru, nazwa własna apteki");
  }
}

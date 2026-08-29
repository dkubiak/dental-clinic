package com.dentalclinic.patient.medicalhistory;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T006 — {@code hasCriticalAllergyAlert} (data-model.md PatientRecord derived field, FR-005/FR-006)
 * is true only when a CURRENT + CRITICAL allergy entry exists.
 */
class CriticalAllergyAlertTest extends PostgresIntegrationTestBase {

  @Autowired private MedicalHistoryService medicalHistoryService;
  @Autowired private PatientCreateService patientCreateService;

  private UUID createPatient() {
    PatientRecord record =
        patientCreateService.create(
            "Anna",
            "Krytyczna",
            LocalDate.of(1985, 5, 5),
            null,
            "Polna",
            "2",
            "00-001",
            "Warszawa",
            UUID.randomUUID());
    return record.getId();
  }

  @Test
  void noAllergies_yieldsNoAlert() {
    UUID patientId = createPatient();
    assertThat(medicalHistoryService.hasCriticalAllergyAlert(patientId)).isFalse();
  }

  @Test
  void moderateAllergyOnly_yieldsNoAlert() {
    UUID patientId = createPatient();
    medicalHistoryService.addAllergy(
        patientId, "Pyłki", "Katar", AllergySeverity.MODERATE, null, UUID.randomUUID());
    assertThat(medicalHistoryService.hasCriticalAllergyAlert(patientId)).isFalse();
  }

  @Test
  void currentCriticalAllergy_yieldsAlert() {
    UUID patientId = createPatient();
    medicalHistoryService.addAllergy(
        patientId, "Penicylina", "Anafilaksja", AllergySeverity.CRITICAL, null, UUID.randomUUID());
    assertThat(medicalHistoryService.hasCriticalAllergyAlert(patientId)).isTrue();
  }

  @Test
  void supersededCriticalAllergy_yieldsNoAlert_onceCorrectedToModerate() {
    UUID patientId = createPatient();
    AllergyEntry original =
        medicalHistoryService.addAllergy(
            patientId, "Penicylina", "Anafilaksja", AllergySeverity.CRITICAL, null,
            UUID.randomUUID());
    medicalHistoryService.addAllergy(
        patientId,
        "Penicylina",
        "Anafilaksja",
        AllergySeverity.MODERATE,
        original.getId(),
        UUID.randomUUID());

    assertThat(medicalHistoryService.hasCriticalAllergyAlert(patientId)).isFalse();
  }
}

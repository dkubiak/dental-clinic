package com.dentalclinic.patient.api;

import com.dentalclinic.patient.record.PatientRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** contracts/patient-api.yaml PatientDetail — full basic-data projection (FR-001/FR-011). */
public record PatientDetailResponse(
    UUID id,
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String pesel,
    String addressStreet,
    String addressBuildingNo,
    String addressPostalCode,
    String addressCity,
    Instant createdAt,
    Instant updatedAt,
    boolean hasCriticalAllergyAlert) {

  /**
   * Added by feature 004 (004-patient-medical-history) — {@code hasCriticalAllergyAlert} carries no
   * clinical detail by construction, so it's safe on a response RECEPTION already reads
   * (research.md #5).
   */
  public static PatientDetailResponse from(PatientRecord record, boolean hasCriticalAllergyAlert) {
    return new PatientDetailResponse(
        record.getId(),
        record.getFirstName(),
        record.getLastName(),
        record.getDateOfBirth(),
        record.getPesel(),
        record.getAddressStreet(),
        record.getAddressBuildingNo(),
        record.getAddressPostalCode(),
        record.getAddressCity(),
        record.getCreatedAt(),
        record.getUpdatedAt(),
        hasCriticalAllergyAlert);
  }
}

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
    Instant updatedAt) {

  public static PatientDetailResponse from(PatientRecord record) {
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
        record.getUpdatedAt());
  }
}

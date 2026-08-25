package com.dentalclinic.patient.api;

import com.dentalclinic.patient.record.PatientRecord;
import java.time.LocalDate;
import java.util.UUID;

/**
 * contracts/patient-api.yaml PatientSummary — basic-data read projection (FR-012 search results).
 */
public record PatientSummaryResponse(
    UUID id, String firstName, String lastName, LocalDate dateOfBirth, String pesel) {

  public static PatientSummaryResponse from(PatientRecord record) {
    return new PatientSummaryResponse(
        record.getId(),
        record.getFirstName(),
        record.getLastName(),
        record.getDateOfBirth(),
        record.getPesel());
  }
}

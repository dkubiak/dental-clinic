package com.dentalclinic.patient.record;

import java.time.LocalDate;

/**
 * Before/after JSON snapshot of a {@link PatientRecord}'s basic data for audit entries
 * (FR-007/FR-011) — deliberately excludes id/createdAt/createdBy/updatedAt/updatedBy, which never
 * change across an edit and would just add noise to the audit diff.
 */
record PatientAuditSnapshot(
    String firstName,
    String lastName,
    LocalDate dateOfBirth,
    String pesel,
    String addressStreet,
    String addressBuildingNo,
    String addressPostalCode,
    String addressCity) {

  static PatientAuditSnapshot of(PatientRecord record) {
    return new PatientAuditSnapshot(
        record.getFirstName(),
        record.getLastName(),
        record.getDateOfBirth(),
        record.getPesel(),
        record.getAddressStreet(),
        record.getAddressBuildingNo(),
        record.getAddressPostalCode(),
        record.getAddressCity());
  }
}

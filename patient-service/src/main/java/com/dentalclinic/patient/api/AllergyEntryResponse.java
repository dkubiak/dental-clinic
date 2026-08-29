package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.AllergyEntry;
import com.dentalclinic.patient.medicalhistory.AllergySeverity;
import com.dentalclinic.patient.medicalhistory.RecordStatus;
import java.time.Instant;
import java.util.UUID;

/** contracts/patient-api.yaml AllergyEntry schema. */
public record AllergyEntryResponse(
    UUID id,
    String substance,
    String reactionType,
    AllergySeverity severity,
    RecordStatus recordStatus,
    UUID supersedesEntryId,
    Instant createdAt) {

  public static AllergyEntryResponse from(AllergyEntry entry) {
    return new AllergyEntryResponse(
        entry.getId(),
        entry.getSubstance(),
        entry.getReactionType(),
        entry.getSeverity(),
        entry.getRecordStatus(),
        entry.getSupersedesEntryId(),
        entry.getCreatedAt());
  }
}

package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.MedicationEntry;
import com.dentalclinic.patient.medicalhistory.RecordStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** contracts/patient-api.yaml MedicationEntry schema. */
public record MedicationEntryResponse(
    UUID id,
    String name,
    String dosage,
    LocalDate startDate,
    RecordStatus recordStatus,
    UUID supersedesEntryId,
    Instant createdAt) {

  public static MedicationEntryResponse from(MedicationEntry entry) {
    return new MedicationEntryResponse(
        entry.getId(),
        entry.getName(),
        entry.getDosage(),
        entry.getStartDate(),
        entry.getRecordStatus(),
        entry.getSupersedesEntryId(),
        entry.getCreatedAt());
  }
}

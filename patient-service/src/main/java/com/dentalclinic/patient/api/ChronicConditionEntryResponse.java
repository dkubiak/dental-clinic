package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.ChronicConditionEntry;
import com.dentalclinic.patient.medicalhistory.ChronicConditionStatus;
import com.dentalclinic.patient.medicalhistory.RecordStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** contracts/patient-api.yaml ChronicConditionEntry schema. */
public record ChronicConditionEntryResponse(
    UUID id,
    String name,
    ChronicConditionStatus clinicalStatus,
    LocalDate diagnosisDate,
    RecordStatus recordStatus,
    UUID supersedesEntryId,
    Instant createdAt) {

  public static ChronicConditionEntryResponse from(ChronicConditionEntry entry) {
    return new ChronicConditionEntryResponse(
        entry.getId(),
        entry.getName(),
        entry.getClinicalStatus(),
        entry.getDiagnosisDate(),
        entry.getRecordStatus(),
        entry.getSupersedesEntryId(),
        entry.getCreatedAt());
  }
}

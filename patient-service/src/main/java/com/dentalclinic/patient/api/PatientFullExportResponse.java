package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.AllergySeverity;
import com.dentalclinic.patient.medicalhistory.RecordStatus;
import com.dentalclinic.patient.rodo.PatientExportService.PatientExport;
import java.util.List;

/**
 * contracts/patient-api.yaml PatientFullExport schema (FR-009). {@code allergies} is added by
 * feature 004 — full history (current and superseded), not just the current view (research.md #6
 * of 004).
 */
public record PatientFullExportResponse(
    PatientDetailResponse patient,
    List<ToothStateResponse> toothChart,
    List<Object> visitHistory,
    List<AllergyEntryResponse> allergies,
    List<MedicationEntryResponse> medications,
    List<ChronicConditionEntryResponse> chronicConditions) {

  public static PatientFullExportResponse from(PatientExport export) {
    boolean hasCriticalAllergyAlert =
        export.allergies().stream()
            .anyMatch(
                a -> a.getRecordStatus() == RecordStatus.CURRENT
                    && a.getSeverity() == AllergySeverity.CRITICAL);
    return new PatientFullExportResponse(
        PatientDetailResponse.from(export.patient(), hasCriticalAllergyAlert),
        export.toothChart().stream().map(ToothStateResponse::from).toList(),
        List.of(),
        export.allergies().stream().map(AllergyEntryResponse::from).toList(),
        export.medications().stream().map(MedicationEntryResponse::from).toList(),
        export.chronicConditions().stream().map(ChronicConditionEntryResponse::from).toList());
  }
}

package com.dentalclinic.patient.api;

import com.dentalclinic.patient.rodo.PatientExportService.PatientExport;
import java.util.List;

/** contracts/patient-api.yaml PatientFullExport schema (FR-009). */
public record PatientFullExportResponse(
    PatientDetailResponse patient, List<ToothStateResponse> toothChart, List<Object> visitHistory) {

  public static PatientFullExportResponse from(PatientExport export) {
    return new PatientFullExportResponse(
        PatientDetailResponse.from(export.patient()),
        export.toothChart().stream().map(ToothStateResponse::from).toList(),
        List.of());
  }
}

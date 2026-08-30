package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.DiagnosisCatalogEntry;
import com.dentalclinic.patient.toothchart.ToothFinding;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** contracts/patient-api.yaml ToothFinding schema. */
public record ToothFindingResponse(
    UUID id,
    int fdiNumber,
    DiagnosisCatalogEntryResponse diagnosisCatalogEntry,
    List<String> surfaces,
    UUID rootCanalId,
    String severity,
    String freeTextDescription,
    String note,
    LocalDate diagnosisDate,
    LocalDate resolvedDate,
    String clinicalStatus,
    String recordStatus,
    UUID supersedesFindingId,
    UUID authorAccountId,
    String authorRole,
    Instant createdAt) {

  public static ToothFindingResponse from(
      ToothFinding finding, int fdiNumber, DiagnosisCatalogEntry entry) {
    return new ToothFindingResponse(
        finding.getId(),
        fdiNumber,
        DiagnosisCatalogEntryResponse.from(entry),
        finding.getSurfaces() == null
            ? null
            : finding.getSurfaces().stream().map(Enum::name).toList(),
        finding.getRootCanalId(),
        finding.getSeverity(),
        finding.getFreeTextDescription(),
        finding.getNote(),
        finding.getDiagnosisDate(),
        finding.getResolvedDate(),
        finding.getClinicalStatus().name(),
        finding.getRecordStatus().name(),
        finding.getSupersedesFindingId(),
        finding.getAuthorAccountId(),
        finding.getAuthorRole().name(),
        finding.getCreatedAt());
  }
}

package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.DiagnosisCatalogEntry;
import java.util.List;
import java.util.UUID;

/** contracts/patient-api.yaml DiagnosisCatalogEntry schema. */
public record DiagnosisCatalogEntryResponse(
    UUID id,
    String code,
    String namePl,
    String category,
    String anatomicalScope,
    String layer,
    String icd10Code,
    List<String> severityOptions,
    boolean allowedForMissingTooth,
    boolean deciduousAllowed,
    boolean quickAccess,
    boolean requiresFreeText) {

  public static DiagnosisCatalogEntryResponse from(DiagnosisCatalogEntry entry) {
    return new DiagnosisCatalogEntryResponse(
        entry.getId(),
        entry.getCode(),
        entry.getNamePl(),
        entry.getCategory().name(),
        entry.getAnatomicalScope().name(),
        entry.getLayer().name(),
        entry.getIcd10Code(),
        entry.getSeverityOptions() == null ? null : List.of(entry.getSeverityOptions()),
        entry.isAllowedForMissingTooth(),
        entry.isDeciduousAllowed(),
        entry.isQuickAccess(),
        entry.isRequiresFreeText());
  }
}

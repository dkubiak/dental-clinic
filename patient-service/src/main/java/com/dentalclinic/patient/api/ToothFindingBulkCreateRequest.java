package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothSurface;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * contracts/patient-api.yaml ToothFindingBulkCreateRequest schema (FR-004a-c, US6). The {@code
 * fdiNumbers} list is resolved client-side from a multi-select gesture (quadrant/arch/ segment
 * shortcuts or drag-select) before the call is made.
 */
public record ToothFindingBulkCreateRequest(
    List<Integer> fdiNumbers,
    UUID diagnosisCatalogEntryId,
    List<ToothSurface> surfaces,
    String severity,
    String freeTextDescription,
    String note,
    LocalDate diagnosisDate) {}

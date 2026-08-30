package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothSurface;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * contracts/patient-api.yaml ToothFindingCreateRequest schema — also the body shape for {@code
 * .../correct} (FR-033), where {@code fdiNumber} is ignored (the target position is fixed to the
 * finding being corrected).
 */
public record ToothFindingCreateRequest(
    int fdiNumber,
    UUID diagnosisCatalogEntryId,
    List<ToothSurface> surfaces,
    UUID rootCanalId,
    String severity,
    String freeTextDescription,
    String note,
    LocalDate diagnosisDate) {}

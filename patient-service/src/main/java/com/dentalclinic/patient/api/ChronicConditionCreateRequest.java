package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.ChronicConditionStatus;
import java.time.LocalDate;
import java.util.UUID;

/** contracts/patient-api.yaml ChronicConditionCreateRequest schema (FR-003/FR-010). */
public record ChronicConditionCreateRequest(
    String name,
    ChronicConditionStatus clinicalStatus,
    LocalDate diagnosisDate,
    UUID supersedesEntryId) {}

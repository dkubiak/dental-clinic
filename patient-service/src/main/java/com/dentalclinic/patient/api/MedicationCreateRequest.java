package com.dentalclinic.patient.api;

import java.time.LocalDate;
import java.util.UUID;

/** contracts/patient-api.yaml MedicationCreateRequest schema (FR-002/FR-010). */
public record MedicationCreateRequest(
    String name, String dosage, LocalDate startDate, UUID supersedesEntryId) {}

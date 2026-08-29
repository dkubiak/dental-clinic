package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.AllergySeverity;
import java.util.UUID;

/** contracts/patient-api.yaml AllergyCreateRequest schema (FR-001/FR-010). */
public record AllergyCreateRequest(
    String substance, String reactionType, AllergySeverity severity, UUID supersedesEntryId) {}

package com.dentalclinic.patient.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * {@code POST /patients} request body (FR-001/FR-002, contracts/patient-api.yaml). PESEL format and
 * checksum are validated by {@code PeselValidator} in the service layer, not here — the checksum
 * algorithm isn't expressible as a simple bean-validation annotation.
 */
public record PatientCreateRequest(
    @NotBlank String firstName,
    @NotBlank String lastName,
    @NotNull LocalDate dateOfBirth,
    String pesel,
    @NotBlank String addressStreet,
    @NotBlank String addressBuildingNo,
    @NotBlank String addressPostalCode,
    @NotBlank String addressCity) {}

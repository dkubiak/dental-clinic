package com.dentalclinic.patient.api;

import java.time.LocalDate;

/** contracts/patient-api.yaml body for {@code POST .../findings/{id}/close} (FR-032). */
public record FindingCloseRequest(LocalDate resolvedDate, String note) {}

package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothPresence;
import java.time.LocalDate;

/**
 * contracts/patient-api.yaml body for {@code PATCH .../positions/{fdi}/presence} (FR-038).
 * {@code expectedVersion} is the optimistic-concurrency token echoed back from the last read
 * (research.md D7, FR-070).
 */
public record PositionPresencePatchRequest(
    ToothPresence presence, LocalDate presenceDate, int expectedVersion) {}

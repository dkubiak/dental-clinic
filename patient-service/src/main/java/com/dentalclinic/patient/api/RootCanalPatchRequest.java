package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.RootCanalState;

/**
 * contracts/patient-api.yaml body for {@code PATCH .../positions/{fdi}/canals/{canalId}}
 * (FR-065/FR-066). {@code expectedVersion} is the optimistic-concurrency token (research.md D7,
 * FR-070).
 */
public record RootCanalPatchRequest(String name, RootCanalState state, int expectedVersion) {}

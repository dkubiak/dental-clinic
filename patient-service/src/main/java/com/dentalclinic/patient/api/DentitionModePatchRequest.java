package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.DentitionMode;

/**
 * contracts/patient-api.yaml body for {@code PATCH .../tooth-chart/dentition-mode} (FR-044/FR-045).
 */
public record DentitionModePatchRequest(DentitionMode dentitionMode) {}

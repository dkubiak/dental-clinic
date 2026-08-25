package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothState;
import com.dentalclinic.patient.toothchart.ToothStatus;
import java.time.Instant;

/** contracts/patient-api.yaml ToothState schema. */
public record ToothStateResponse(int toothNumber, ToothStatus status, Instant updatedAt) {

  public static ToothStateResponse from(ToothState toothState) {
    return new ToothStateResponse(
        toothState.getToothNumber(), toothState.getStatus(), toothState.getUpdatedAt());
  }
}

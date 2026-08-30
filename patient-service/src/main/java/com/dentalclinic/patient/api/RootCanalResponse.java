package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.RootCanal;
import java.util.UUID;

/** contracts/patient-api.yaml RootCanal schema. */
public record RootCanalResponse(UUID id, String name, String state, boolean removed, int version) {

  public static RootCanalResponse from(RootCanal canal) {
    return new RootCanalResponse(
        canal.getId(), canal.getName(), canal.getState().name(), canal.isRemoved(), canal.getVersion());
  }
}

package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothChartService.PositionView;
import java.time.LocalDate;
import java.util.List;

/** contracts/patient-api.yaml ToothPosition schema. {@code currentFindings} is populated by US1
 * (T058) once {@link ToothFindingResponse} exists. */
public record ToothPositionResponse(
    int fdiNumber,
    String dentitionType,
    String toothType,
    String presence,
    LocalDate presenceDate,
    int version,
    List<RootCanalResponse> canals,
    List<ToothFindingResponse> currentFindings) {

  public static ToothPositionResponse from(PositionView view) {
    return new ToothPositionResponse(
        view.fdiNumber(),
        view.dentitionType().name(),
        view.toothType().name(),
        view.presence().name(),
        view.position().getPresenceDate(),
        view.position().getVersion(),
        view.canals().stream().map(RootCanalResponse::from).toList(),
        view.currentFindings().stream()
            .map(fv -> ToothFindingResponse.from(fv.finding(), view.fdiNumber(), fv.entry()))
            .toList());
  }
}

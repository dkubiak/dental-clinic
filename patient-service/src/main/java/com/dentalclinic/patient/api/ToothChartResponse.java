package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothChartService.ChartView;
import java.util.List;
import java.util.UUID;

/** contracts/patient-api.yaml ToothChart schema. */
public record ToothChartResponse(UUID patientId, String dentitionMode, List<ToothPositionResponse> positions) {

  public static ToothChartResponse from(ChartView view) {
    return new ToothChartResponse(
        view.chart().getPatientRecordId(),
        view.chart().getDentitionMode().name(),
        view.positions().stream().map(ToothPositionResponse::from).toList());
  }
}

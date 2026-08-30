package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothFindingService;
import java.util.List;

/** contracts/patient-api.yaml ToothFindingBulkResult schema (FR-004a, US6 scenario 3/4). */
public record ToothFindingBulkResultResponse(
    List<ToothFindingResponse> created, List<SkippedPositionResponse> skipped) {

  public record SkippedPositionResponse(int fdiNumber, String reason) {}

  public static ToothFindingBulkResultResponse from(
      ToothFindingService.BulkResult result, ToothFindingService service) {
    return new ToothFindingBulkResultResponse(
        result.created().stream()
            .map(
                finding ->
                    ToothFindingController.toResponse(
                        finding, service.fdiNumberOf(finding), service))
            .toList(),
        result.skipped().stream()
            .map(s -> new SkippedPositionResponse(s.fdiNumber(), s.reason()))
            .toList());
  }
}

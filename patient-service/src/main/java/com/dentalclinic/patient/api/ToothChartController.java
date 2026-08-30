package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothChartService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /patients/{patientId}/tooth-chart[/positions/{fdiNumber}/history]},
 * {@code PATCH .../positions/{fdiNumber}/presence}, {@code PATCH .../dentition-mode} per
 * contracts/patient-api.yaml (FR-005/FR-034/FR-038/FR-044). {@code @PreAuthorize} restricted to
 * DOCTOR/ASSISTANT (RECEPTION excluded, rbac-policy.md); deny→404.
 */
@RestController
public class ToothChartController {

  private final ToothChartService toothChartService;

  public ToothChartController(ToothChartService toothChartService) {
    this.toothChartService = toothChartService;
  }

  @GetMapping("/patients/{patientId}/tooth-chart")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothChartResponse> getChart(
      @PathVariable UUID patientId, Principal principal) {
    ToothChartService.ChartView view = toothChartService.getChart(patientId, actorId(principal));
    return ResponseEntity.ok(ToothChartResponse.from(view));
  }

  @PatchMapping("/patients/{patientId}/tooth-chart/positions/{fdiNumber}/presence")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothPositionResponse> changePresence(
      @PathVariable UUID patientId,
      @PathVariable int fdiNumber,
      @RequestBody PositionPresencePatchRequest request,
      Principal principal) {
    var view =
        toothChartService.changePresence(
            patientId,
            fdiNumber,
            request.presence(),
            request.presenceDate(),
            request.expectedVersion(),
            actorId(principal));
    return ResponseEntity.ok(ToothPositionResponse.from(view));
  }

  @PatchMapping("/patients/{patientId}/tooth-chart/dentition-mode")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothChartResponse> changeDentitionMode(
      @PathVariable UUID patientId,
      @RequestBody DentitionModePatchRequest request,
      Principal principal) {
    ToothChartService.ChartView view =
        toothChartService.changeDentitionMode(
            patientId, request.dentitionMode(), actorId(principal));
    return ResponseEntity.ok(ToothChartResponse.from(view));
  }

  @GetMapping("/patients/{patientId}/tooth-chart/positions/{fdiNumber}/history")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<ToothFindingResponse>> getPositionHistory(
      @PathVariable UUID patientId, @PathVariable int fdiNumber, Principal principal) {
    List<ToothChartService.FindingView> history =
        toothChartService.getPositionHistory(patientId, fdiNumber, actorId(principal));
    List<ToothFindingResponse> response =
        history.stream()
            .map(fv -> ToothFindingResponse.from(fv.finding(), fdiNumber, fv.entry()))
            .toList();
    return ResponseEntity.ok(response);
  }

  static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}

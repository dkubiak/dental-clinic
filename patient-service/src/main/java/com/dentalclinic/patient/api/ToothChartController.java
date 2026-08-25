package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.ToothChartService;
import com.dentalclinic.patient.toothchart.ToothState;
import com.dentalclinic.patient.toothchart.ToothStatus;
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
 * {@code GET/PATCH /patients/{patientId}/tooth-chart[/{toothNumber}]} per
 * contracts/patient-api.yaml — US2 (FR-005/FR-006). {@code @PreAuthorize} restricted to
 * DOCTOR/ASSISTANT (RECEPTION excluded, rbac-policy.md).
 */
@RestController
public class ToothChartController {

  private final ToothChartService toothChartService;

  public ToothChartController(ToothChartService toothChartService) {
    this.toothChartService = toothChartService;
  }

  @GetMapping("/patients/{patientId}/tooth-chart")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<ToothStateResponse>> getChart(
      @PathVariable UUID patientId, Principal principal) {
    List<ToothState> teeth = toothChartService.getChart(patientId, actorId(principal));
    return ResponseEntity.ok(teeth.stream().map(ToothStateResponse::from).toList());
  }

  @PatchMapping("/patients/{patientId}/tooth-chart/{toothNumber}")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothStateResponse> setStatus(
      @PathVariable UUID patientId,
      @PathVariable int toothNumber,
      @RequestBody ToothStatusRequest request,
      Principal principal) {
    ToothState tooth =
        toothChartService.setStatus(patientId, toothNumber, request.status(), actorId(principal));
    return ResponseEntity.ok(ToothStateResponse.from(tooth));
  }

  private static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }

  public record ToothStatusRequest(ToothStatus status) {}
}

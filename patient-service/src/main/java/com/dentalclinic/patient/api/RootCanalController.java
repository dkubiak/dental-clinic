package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.RootCanal;
import com.dentalclinic.patient.toothchart.RootCanalService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST/PATCH/DELETE .../positions/{fdiNumber}/canals[/{canalId}]} per
 * contracts/patient-api.yaml (FR-065/FR-066/FR-068). {@code @PreAuthorize} restricted to
 * DOCTOR/ASSISTANT (rbac-policy.md); deny→404.
 */
@RestController
public class RootCanalController {

  private final RootCanalService rootCanalService;

  public RootCanalController(RootCanalService rootCanalService) {
    this.rootCanalService = rootCanalService;
  }

  @PostMapping("/patients/{patientId}/tooth-chart/positions/{fdiNumber}/canals")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<RootCanalResponse> addCanal(
      @PathVariable UUID patientId,
      @PathVariable int fdiNumber,
      @RequestBody RootCanalCreateRequest request,
      Principal principal) {
    RootCanal canal =
        rootCanalService.addCanal(patientId, fdiNumber, request.name(), actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(RootCanalResponse.from(canal));
  }

  @PatchMapping("/patients/{patientId}/tooth-chart/positions/{fdiNumber}/canals/{canalId}")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<RootCanalResponse> updateCanal(
      @PathVariable UUID patientId,
      @PathVariable int fdiNumber,
      @PathVariable UUID canalId,
      @RequestBody RootCanalPatchRequest request,
      Principal principal) {
    RootCanal canal =
        rootCanalService.updateCanal(
            patientId,
            fdiNumber,
            canalId,
            request.name(),
            request.state(),
            request.expectedVersion(),
            actorId(principal));
    return ResponseEntity.ok(RootCanalResponse.from(canal));
  }

  @DeleteMapping("/patients/{patientId}/tooth-chart/positions/{fdiNumber}/canals/{canalId}")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<Void> removeCanal(
      @PathVariable UUID patientId,
      @PathVariable int fdiNumber,
      @PathVariable UUID canalId,
      Principal principal) {
    rootCanalService.removeCanal(patientId, fdiNumber, canalId, actorId(principal));
    return ResponseEntity.noContent().build();
  }

  static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}

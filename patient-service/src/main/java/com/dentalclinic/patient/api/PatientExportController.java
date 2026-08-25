package com.dentalclinic.patient.api;

import com.dentalclinic.patient.rodo.PatientExportService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /patients/{patientId}/export} per contracts/patient-api.yaml (FR-009, RODO
 * subject-access request). {@code @PreAuthorize} restricted to DOCTOR only (research.md #6).
 */
@RestController
public class PatientExportController {

  private final PatientExportService patientExportService;

  public PatientExportController(PatientExportService patientExportService) {
    this.patientExportService = patientExportService;
  }

  @PostMapping("/patients/{patientId}/export")
  @PreAuthorize("hasRole('DOCTOR')")
  public ResponseEntity<PatientFullExportResponse> export(
      @PathVariable UUID patientId, Principal principal) {
    var export = patientExportService.export(patientId, actorId(principal));
    return ResponseEntity.ok(PatientFullExportResponse.from(export));
  }

  private static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}

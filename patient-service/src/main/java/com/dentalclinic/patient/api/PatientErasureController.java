package com.dentalclinic.patient.api;

import com.dentalclinic.patient.rodo.PatientErasureService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /patients/{patientId}/erasure-request} per contracts/patient-api.yaml (FR-010, RODO
 * erasure). {@code @PreAuthorize} restricted to DOCTOR only (same rationale as export).
 */
@RestController
public class PatientErasureController {

  private final PatientErasureService patientErasureService;

  public PatientErasureController(PatientErasureService patientErasureService) {
    this.patientErasureService = patientErasureService;
  }

  @PostMapping("/patients/{patientId}/erasure-request")
  @PreAuthorize("hasRole('DOCTOR')")
  public ResponseEntity<Void> requestErasure(@PathVariable UUID patientId, Principal principal) {
    patientErasureService.requestErasure(patientId, actorId(principal));
    return ResponseEntity.accepted().build();
  }

  private static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}

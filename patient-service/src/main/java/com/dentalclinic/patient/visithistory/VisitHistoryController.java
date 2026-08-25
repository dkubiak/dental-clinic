package com.dentalclinic.patient.visithistory;

import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /patients/{patientId}/visit-history} per contracts/patient-api.yaml — US3 (FR-004).
 * Always returns an empty array in this version: no visit data exists yet, and this section is
 * superseded by a future, separately specified visits module (spec.md). {@code @PreAuthorize}
 * restricted to RECEPTION/DOCTOR (rbac-policy.md) — ASSISTANT/ADMINISTRATOR excluded.
 */
@RestController
public class VisitHistoryController {

  @GetMapping("/patients/{patientId}/visit-history")
  @PreAuthorize("hasAnyRole('RECEPTION', 'DOCTOR')")
  public ResponseEntity<List<Object>> getVisitHistory(@PathVariable UUID patientId) {
    return ResponseEntity.ok(List.of());
  }
}

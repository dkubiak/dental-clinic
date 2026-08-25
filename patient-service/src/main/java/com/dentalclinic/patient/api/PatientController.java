package com.dentalclinic.patient.api;

import com.dentalclinic.patient.record.PatientCreateService;
import com.dentalclinic.patient.record.PatientRecord;
import com.dentalclinic.patient.record.PatientSearchService;
import com.dentalclinic.patient.record.PatientUpdateService;
import jakarta.validation.Valid;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST/GET /patients}, {@code GET/PATCH /patients/{id}} per contracts/patient-api.yaml — US1
 * (FR-001/FR-002/FR-003/FR-011/FR-012). {@code @PreAuthorize} rules mirror rbac-policy.md's
 * feature-002 rows exactly (create/edit: RECEPTION, DOCTOR; read: + ASSISTANT).
 */
@RestController
public class PatientController {

  private final PatientCreateService patientCreateService;
  private final PatientSearchService patientSearchService;
  private final PatientUpdateService patientUpdateService;

  public PatientController(
      PatientCreateService patientCreateService,
      PatientSearchService patientSearchService,
      PatientUpdateService patientUpdateService) {
    this.patientCreateService = patientCreateService;
    this.patientSearchService = patientSearchService;
    this.patientUpdateService = patientUpdateService;
  }

  @GetMapping("/patients")
  @PreAuthorize("hasAnyRole('RECEPTION', 'DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<PatientSummaryResponse>> search(
      @RequestParam String q, Principal principal) {
    List<PatientRecord> results = patientSearchService.search(q, actorId(principal));
    return ResponseEntity.ok(results.stream().map(PatientSummaryResponse::from).toList());
  }

  @PostMapping("/patients")
  @PreAuthorize("hasAnyRole('RECEPTION', 'DOCTOR')")
  public ResponseEntity<PatientDetailResponse> create(
      @Valid @RequestBody PatientCreateRequest request, Principal principal) {
    PatientRecord record =
        patientCreateService.create(
            request.firstName(),
            request.lastName(),
            request.dateOfBirth(),
            request.pesel(),
            request.addressStreet(),
            request.addressBuildingNo(),
            request.addressPostalCode(),
            request.addressCity(),
            actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(PatientDetailResponse.from(record));
  }

  @GetMapping("/patients/{id}")
  @PreAuthorize("hasAnyRole('RECEPTION', 'DOCTOR', 'ASSISTANT')")
  public ResponseEntity<PatientDetailResponse> get(@PathVariable UUID id, Principal principal) {
    PatientRecord record = patientSearchService.getById(id, actorId(principal));
    return ResponseEntity.ok(PatientDetailResponse.from(record));
  }

  @PatchMapping("/patients/{id}")
  @PreAuthorize("hasAnyRole('RECEPTION', 'DOCTOR')")
  public ResponseEntity<PatientDetailResponse> update(
      @PathVariable UUID id,
      @Valid @RequestBody PatientCreateRequest request,
      Principal principal) {
    PatientRecord record =
        patientUpdateService.update(
            id,
            request.firstName(),
            request.lastName(),
            request.dateOfBirth(),
            request.pesel(),
            request.addressStreet(),
            request.addressBuildingNo(),
            request.addressPostalCode(),
            request.addressCity(),
            actorId(principal));
    return ResponseEntity.ok(PatientDetailResponse.from(record));
  }

  private static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}

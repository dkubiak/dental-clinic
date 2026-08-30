package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.FindingAuthorRole;
import com.dentalclinic.patient.toothchart.ToothFinding;
import com.dentalclinic.patient.toothchart.ToothFindingService;
import java.security.Principal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code POST /patients/{patientId}/tooth-chart/findings[/bulk|/{id}/close|/{id}/correct]} per
 * contracts/patient-api.yaml (FR-022/FR-030/FR-057/FR-058). {@code @PreAuthorize} restricted to
 * DOCTOR/ASSISTANT with identical scope (FR-057) — {@code authorRole} snapshots which role the
 * caller acted in at write time. No {@code @PatchMapping}/{@code @DeleteMapping} exists anywhere in
 * this class — corrections are always a new POST (FR-030, research.md D3).
 */
@RestController
public class ToothFindingController {

  private final ToothFindingService toothFindingService;

  public ToothFindingController(ToothFindingService toothFindingService) {
    this.toothFindingService = toothFindingService;
  }

  @PostMapping("/patients/{patientId}/tooth-chart/findings")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothFindingResponse> addFinding(
      @PathVariable UUID patientId,
      @RequestBody ToothFindingCreateRequest request,
      Principal principal,
      Authentication authentication) {
    ToothFinding finding =
        toothFindingService.addFinding(
            patientId,
            request.fdiNumber(),
            request.diagnosisCatalogEntryId(),
            request.surfaces(),
            request.rootCanalId(),
            request.severity(),
            request.freeTextDescription(),
            request.note(),
            request.diagnosisDate(),
            actorId(principal),
            authorRole(authentication));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toResponse(finding, request.fdiNumber(), toothFindingService));
  }

  @PostMapping("/patients/{patientId}/tooth-chart/findings/{findingId}/close")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothFindingResponse> closeFinding(
      @PathVariable UUID patientId,
      @PathVariable UUID findingId,
      @RequestBody FindingCloseRequest request,
      Principal principal) {
    ToothFinding finding =
        toothFindingService.closeFinding(
            patientId, findingId, request.resolvedDate(), request.note(), actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toResponse(finding, fdiNumberOf(finding), toothFindingService));
  }

  @PostMapping("/patients/{patientId}/tooth-chart/findings/{findingId}/correct")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<ToothFindingResponse> correctFinding(
      @PathVariable UUID patientId,
      @PathVariable UUID findingId,
      @RequestBody ToothFindingCreateRequest request,
      Principal principal,
      Authentication authentication) {
    ToothFinding finding =
        toothFindingService.correctFinding(
            patientId,
            findingId,
            request.diagnosisCatalogEntryId(),
            request.surfaces(),
            request.rootCanalId(),
            request.severity(),
            request.freeTextDescription(),
            request.note(),
            request.diagnosisDate(),
            actorId(principal),
            authorRole(authentication));
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(toResponse(finding, fdiNumberOf(finding), toothFindingService));
  }

  private int fdiNumberOf(ToothFinding finding) {
    return toothFindingService.fdiNumberOf(finding);
  }

  static ToothFindingResponse toResponse(
      ToothFinding finding, int fdiNumber, ToothFindingService service) {
    return ToothFindingResponse.from(
        finding, fdiNumber, service.requireCatalogEntry(finding.getDiagnosisCatalogEntryId()));
  }

  static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }

  static FindingAuthorRole authorRole(Authentication authentication) {
    boolean isDoctor =
        authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_DOCTOR"));
    return isDoctor ? FindingAuthorRole.DOCTOR : FindingAuthorRole.ASSISTANT;
  }
}

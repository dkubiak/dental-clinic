package com.dentalclinic.patient.api;

import com.dentalclinic.patient.medicalhistory.MedicalHistoryService;
import java.security.Principal;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET/POST /patients/{patientId}/{allergies,medications,chronic-conditions}[/history]} per
 * contracts/patient-api.yaml — US1/US2/US3 (FR-001/FR-002/FR-003/FR-004/FR-005/FR-010).
 * {@code @PreAuthorize} restricted to DOCTOR/ASSISTANT for reads, DOCTOR only for writes (RECEPTION
 * excluded, rbac-policy.md rule 7); deny→404 per rule 2. No {@code @PatchMapping}/{@code
 * @DeleteMapping} exists anywhere in this class — corrections are always a new POST (FR-010).
 */
@RestController
public class MedicalHistoryController {

  private final MedicalHistoryService medicalHistoryService;

  public MedicalHistoryController(MedicalHistoryService medicalHistoryService) {
    this.medicalHistoryService = medicalHistoryService;
  }

  @GetMapping("/patients/{patientId}/allergies")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<AllergyEntryResponse>> getCurrentAllergies(
      @PathVariable UUID patientId, Principal principal) {
    List<AllergyEntryResponse> entries =
        medicalHistoryService.getCurrentAllergies(patientId, actorId(principal)).stream()
            .map(AllergyEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }

  @GetMapping("/patients/{patientId}/allergies/history")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<AllergyEntryResponse>> getAllergyHistory(
      @PathVariable UUID patientId, Principal principal) {
    List<AllergyEntryResponse> entries =
        medicalHistoryService.getAllergyHistory(patientId, actorId(principal)).stream()
            .map(AllergyEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }

  @PostMapping("/patients/{patientId}/allergies")
  @PreAuthorize("hasRole('DOCTOR')")
  public ResponseEntity<AllergyEntryResponse> addAllergy(
      @PathVariable UUID patientId, @RequestBody AllergyCreateRequest request, Principal principal) {
    var entry =
        medicalHistoryService.addAllergy(
            patientId,
            request.substance(),
            request.reactionType(),
            request.severity(),
            request.supersedesEntryId(),
            actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(AllergyEntryResponse.from(entry));
  }

  @GetMapping("/patients/{patientId}/medications")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<MedicationEntryResponse>> getCurrentMedications(
      @PathVariable UUID patientId, Principal principal) {
    List<MedicationEntryResponse> entries =
        medicalHistoryService.getCurrentMedications(patientId, actorId(principal)).stream()
            .map(MedicationEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }

  @GetMapping("/patients/{patientId}/medications/history")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<MedicationEntryResponse>> getMedicationHistory(
      @PathVariable UUID patientId, Principal principal) {
    List<MedicationEntryResponse> entries =
        medicalHistoryService.getMedicationHistory(patientId, actorId(principal)).stream()
            .map(MedicationEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }

  @PostMapping("/patients/{patientId}/medications")
  @PreAuthorize("hasRole('DOCTOR')")
  public ResponseEntity<MedicationEntryResponse> addMedication(
      @PathVariable UUID patientId,
      @RequestBody MedicationCreateRequest request,
      Principal principal) {
    var entry =
        medicalHistoryService.addMedication(
            patientId,
            request.name(),
            request.dosage(),
            request.startDate(),
            request.supersedesEntryId(),
            actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(MedicationEntryResponse.from(entry));
  }

  @GetMapping("/patients/{patientId}/chronic-conditions")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<ChronicConditionEntryResponse>> getCurrentChronicConditions(
      @PathVariable UUID patientId, Principal principal) {
    List<ChronicConditionEntryResponse> entries =
        medicalHistoryService.getCurrentChronicConditions(patientId, actorId(principal)).stream()
            .map(ChronicConditionEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }

  @GetMapping("/patients/{patientId}/chronic-conditions/history")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<ChronicConditionEntryResponse>> getChronicConditionHistory(
      @PathVariable UUID patientId, Principal principal) {
    List<ChronicConditionEntryResponse> entries =
        medicalHistoryService.getChronicConditionHistory(patientId, actorId(principal)).stream()
            .map(ChronicConditionEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }

  @PostMapping("/patients/{patientId}/chronic-conditions")
  @PreAuthorize("hasRole('DOCTOR')")
  public ResponseEntity<ChronicConditionEntryResponse> addChronicCondition(
      @PathVariable UUID patientId,
      @RequestBody ChronicConditionCreateRequest request,
      Principal principal) {
    var entry =
        medicalHistoryService.addChronicCondition(
            patientId,
            request.name(),
            request.clinicalStatus(),
            request.diagnosisDate(),
            request.supersedesEntryId(),
            actorId(principal));
    return ResponseEntity.status(HttpStatus.CREATED).body(ChronicConditionEntryResponse.from(entry));
  }

  private static UUID actorId(Principal principal) {
    return UUID.fromString(principal.getName());
  }
}

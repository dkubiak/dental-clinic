package com.dentalclinic.patient.api;

import com.dentalclinic.patient.toothchart.DiagnosisCatalogService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /diagnosis-catalog} per contracts/patient-api.yaml (FR-013/FR-020). DOCTOR/ASSISTANT
 * only; no POST/PATCH/DELETE mapping exists anywhere in this class — the catalog is Flyway-seeded,
 * read-only reference data (FR-011, research.md D5).
 */
@RestController
public class DiagnosisCatalogController {

  private final DiagnosisCatalogService diagnosisCatalogService;

  public DiagnosisCatalogController(DiagnosisCatalogService diagnosisCatalogService) {
    this.diagnosisCatalogService = diagnosisCatalogService;
  }

  @GetMapping("/diagnosis-catalog")
  @PreAuthorize("hasAnyRole('DOCTOR', 'ASSISTANT')")
  public ResponseEntity<List<DiagnosisCatalogEntryResponse>> search(
      @RequestParam(required = false) String q,
      @RequestParam(required = false, defaultValue = "false") boolean quickAccessOnly) {
    List<DiagnosisCatalogEntryResponse> entries =
        diagnosisCatalogService.search(q, quickAccessOnly).stream()
            .map(DiagnosisCatalogEntryResponse::from)
            .toList();
    return ResponseEntity.ok(entries);
  }
}

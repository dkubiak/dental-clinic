package com.dentalclinic.patient.toothchart;

import java.util.List;
import org.springframework.stereotype.Service;

/** FR-013/FR-020 — search the read-only, Flyway-seeded diagnosis catalog. */
@Service
public class DiagnosisCatalogService {

  private final DiagnosisCatalogEntryRepository repository;

  public DiagnosisCatalogService(DiagnosisCatalogEntryRepository repository) {
    this.repository = repository;
  }

  /**
   * @param query fragment of {@code namePl} or {@code code} (FR-013); {@code null}/blank returns
   *     the full catalog.
   * @param quickAccessOnly when true, restricts to {@code quickAccess} entries (FR-020).
   */
  public List<DiagnosisCatalogEntry> search(String query, boolean quickAccessOnly) {
    List<DiagnosisCatalogEntry> entries =
        (query == null || query.isBlank())
            ? repository.findAll()
            : repository.findByNamePlContainingIgnoreCaseOrCodeContainingIgnoreCase(query);
    if (quickAccessOnly) {
      entries = entries.stream().filter(DiagnosisCatalogEntry::isQuickAccess).toList();
    }
    return entries;
  }
}

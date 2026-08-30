package com.dentalclinic.patient.toothchart;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface DiagnosisCatalogEntryRepository extends JpaRepository<DiagnosisCatalogEntry, UUID> {

  Optional<DiagnosisCatalogEntry> findByCode(String code);

  /** FR-013 — search by name or code fragment, results narrowing as the user types. */
  @Query(
      "SELECT e FROM DiagnosisCatalogEntry e WHERE"
          + " LOWER(e.namePl) LIKE LOWER(CONCAT('%', :fragment, '%'))"
          + " OR LOWER(e.code) LIKE LOWER(CONCAT('%', :fragment, '%'))")
  List<DiagnosisCatalogEntry> findByNamePlContainingIgnoreCaseOrCodeContainingIgnoreCase(
      String fragment);

  /** FR-020 — the context menu's "najczęstsze" section. */
  List<DiagnosisCatalogEntry> findByQuickAccessTrue();
}

package com.dentalclinic.patient.record;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-012 — search by last-name fragment or exact PESEL, and single-record read by id (FR-001
 * identification use). Both write a {@code PATIENT_RECORD_VIEWED} audit entry (FR-007/SC-003, added
 * during /speckit-analyze remediation) — one entry per search *call* (with the query and hit count
 * in {@code metadata}, not one per matched record) or per detail read.
 */
@Service
public class PatientSearchService {

  private final PatientRecordRepository repository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public PatientSearchService(
      PatientRecordRepository repository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * {@code q} matches an exact PESEL when it looks like one (11 digits); otherwise it's treated as
   * a case-insensitive last-name fragment.
   */
  public List<PatientRecord> search(String q, UUID actorId) {
    List<PatientRecord> results =
        q.matches("\\d{11}")
            ? repository.findByPesel(q).map(List::of).orElseGet(List::of)
            : repository.findByLastNameIgnoreCaseContaining(q);

    auditWriter.append(
        PatientAuditEventType.PATIENT_RECORD_VIEWED,
        actorId,
        null, // no single patient is "the" target of a search call
        null,
        null,
        searchMetadata(q, results.size()));
    return results;
  }

  /**
   * @throws PatientNotFoundException no record with this id exists.
   */
  public PatientRecord getById(UUID id, UUID actorId) {
    PatientRecord record = repository.findById(id).orElseThrow(PatientNotFoundException::new);
    auditWriter.append(
        PatientAuditEventType.PATIENT_RECORD_VIEWED, actorId, record.getId(), null, null, null);
    return record;
  }

  private String searchMetadata(String query, int hitCount) {
    return objectMapper.writeValueAsString(new SearchMetadata(query, hitCount));
  }

  private record SearchMetadata(String query, int hitCount) {}
}

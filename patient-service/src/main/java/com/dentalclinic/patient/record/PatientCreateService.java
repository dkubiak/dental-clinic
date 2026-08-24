package com.dentalclinic.patient.record;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-001/FR-002/FR-003 — creates a new patient record for RECEPTION/DOCTOR (RBAC enforced by {@code
 * PatientController}'s {@code @PreAuthorize}, not here).
 */
@Service
public class PatientCreateService {

  private final PatientRecordRepository repository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public PatientCreateService(
      PatientRecordRepository repository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * @throws InvalidPeselException FR-002 — format/checksum failed (US1 Acceptance Scenario 3).
   * @throws DuplicatePeselException FR-003 — PESEL already exists (US1 Acceptance Scenario 4). Not
   *     checked when {@code pesel} is null (US1 Acceptance Scenario 5, accepted risk).
   */
  @Transactional
  public PatientRecord create(
      String firstName,
      String lastName,
      java.time.LocalDate dateOfBirth,
      String pesel,
      String addressStreet,
      String addressBuildingNo,
      String addressPostalCode,
      String addressCity,
      UUID actorId) {
    if (!PeselValidator.isValid(pesel)) {
      throw new InvalidPeselException();
    }
    if (pesel != null && repository.findByPesel(pesel).isPresent()) {
      throw new DuplicatePeselException();
    }

    PatientRecord record =
        new PatientRecord(
            UUID.randomUUID(),
            firstName,
            lastName,
            dateOfBirth,
            pesel,
            addressStreet,
            addressBuildingNo,
            addressPostalCode,
            addressCity,
            actorId);
    // The DB's own partial unique index on pesel (V1__patient_record.sql) is the authoritative
    // backstop against a race between the findByPesel check above and this insert — translate a
    // concurrent duplicate into the same DuplicatePeselException the pre-check throws, rather
    // than letting a raw DataIntegrityViolationException surface as a 500.
    try {
      repository.saveAndFlush(record);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicatePeselException();
    }

    auditWriter.append(
        PatientAuditEventType.PATIENT_RECORD_CREATED,
        actorId,
        record.getId(),
        null,
        toJson(record),
        null);

    return record;
  }

  private String toJson(PatientRecord record) {
    // ObjectMapper#writeValueAsString throws the unchecked tools.jackson.core.JacksonException
    // in Jackson 3 (Spring Boot 4) — no checked-exception wrapping needed, unlike Jackson 2.
    return objectMapper.writeValueAsString(PatientAuditSnapshot.of(record));
  }
}

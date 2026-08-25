package com.dentalclinic.patient.record;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-011 — edits an existing patient's basic data for RECEPTION/DOCTOR, always audit-logged with
 * before/after state.
 */
@Service
public class PatientUpdateService {

  private final PatientRecordRepository repository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public PatientUpdateService(
      PatientRecordRepository repository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * @throws PatientNotFoundException no record with this id exists.
   * @throws InvalidPeselException FR-002 — format/checksum failed.
   * @throws DuplicatePeselException FR-003 — PESEL already exists on a *different* record.
   */
  @Transactional
  public PatientRecord update(
      UUID id,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String pesel,
      String addressStreet,
      String addressBuildingNo,
      String addressPostalCode,
      String addressCity,
      UUID actorId) {
    PatientRecord record = repository.findById(id).orElseThrow(PatientNotFoundException::new);

    if (!PeselValidator.isValid(pesel)) {
      throw new InvalidPeselException();
    }
    if (pesel != null) {
      repository
          .findByPesel(pesel)
          .filter(existing -> !existing.getId().equals(id))
          .ifPresent(
              existing -> {
                throw new DuplicatePeselException();
              });
    }

    String beforeJson = toJson(record);
    record.updateBasicData(
        firstName,
        lastName,
        dateOfBirth,
        pesel,
        addressStreet,
        addressBuildingNo,
        addressPostalCode,
        addressCity,
        actorId);
    try {
      repository.saveAndFlush(record);
    } catch (DataIntegrityViolationException e) {
      throw new DuplicatePeselException();
    }

    auditWriter.append(
        PatientAuditEventType.PATIENT_RECORD_UPDATED,
        actorId,
        record.getId(),
        beforeJson,
        toJson(record),
        null);
    return record;
  }

  private String toJson(PatientRecord record) {
    return objectMapper.writeValueAsString(PatientAuditSnapshot.of(record));
  }
}

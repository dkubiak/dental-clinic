package com.dentalclinic.patient.medicalhistory;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-001/FR-002/FR-003/FR-010 — add/read/history for all three medical-history sub-resources
 * (allergies, medications, chronic conditions). One service class per research.md #7 (mirrors
 * {@code ToothChartService}'s shape) rather than three near-identical services, since all three
 * share the exact same append-only correction mechanics and audit-writer usage.
 */
@Service
public class MedicalHistoryService {

  private final AllergyEntryRepository allergyEntryRepository;
  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public MedicalHistoryService(
      AllergyEntryRepository allergyEntryRepository,
      PatientRecordRepository patientRecordRepository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.allergyEntryRepository = allergyEntryRepository;
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  private static final String ALLERGY_METADATA = "{\"entryType\":\"ALLERGY\"}";

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<AllergyEntry> getCurrentAllergies(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<AllergyEntry> entries =
        allergyEntryRepository.findByPatientRecordIdAndRecordStatusOrderByCreatedAtDesc(
            patientId, RecordStatus.CURRENT);
    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_ENTRY_VIEWED,
        actorId,
        patientId,
        null,
        null,
        ALLERGY_METADATA);
    return entries;
  }

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<AllergyEntry> getAllergyHistory(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<AllergyEntry> entries = allergyEntryRepository.findByPatientRecordIdOrderByCreatedAtDesc(patientId);
    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_HISTORY_VIEWED,
        actorId,
        patientId,
        null,
        null,
        ALLERGY_METADATA);
    return entries;
  }

  /**
   * FR-010 — if {@code supersedesEntryId} is set, that entry flips to SUPERSEDED in the same
   * transaction as the new CURRENT row's insert.
   *
   * @throws PatientNotFoundException no patient record, or (if set) no superseded entry, exists.
   * @throws IllegalArgumentException {@code substance}/{@code reactionType} is blank (FR-011).
   */
  @Transactional
  public AllergyEntry addAllergy(
      UUID patientId,
      String substance,
      String reactionType,
      AllergySeverity severity,
      UUID supersedesEntryId,
      UUID actorId) {
    requirePatientExists(patientId);
    requireNonBlank(substance, "substance");
    requireNonBlank(reactionType, "reactionType");

    String beforeJson = null;
    if (supersedesEntryId != null) {
      AllergyEntry superseded =
          allergyEntryRepository.findById(supersedesEntryId).orElseThrow(PatientNotFoundException::new);
      beforeJson = toJson(superseded);
      superseded.supersede();
      allergyEntryRepository.save(superseded);
    }

    AllergyEntry entry =
        new AllergyEntry(
            UUID.randomUUID(), patientId, substance, reactionType, severity, supersedesEntryId, actorId);
    allergyEntryRepository.save(entry);

    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_ENTRY_ADDED,
        actorId,
        patientId,
        beforeJson,
        toJson(entry),
        ALLERGY_METADATA);
    return entry;
  }

  /** FR-005/FR-006 — computed per request, no caching/denormalization (data-model.md). */
  public boolean hasCriticalAllergyAlert(UUID patientId) {
    return allergyEntryRepository.existsByPatientRecordIdAndRecordStatusAndSeverity(
        patientId, RecordStatus.CURRENT, AllergySeverity.CRITICAL);
  }

  private void requirePatientExists(UUID patientId) {
    if (!patientRecordRepository.existsById(patientId)) {
      throw new PatientNotFoundException();
    }
  }

  private static void requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
  }

  private String toJson(AllergyEntry entry) {
    return objectMapper.writeValueAsString(
        new AllergySnapshot(
            entry.getSubstance(), entry.getReactionType(), entry.getSeverity(), entry.getRecordStatus()));
  }

  private record AllergySnapshot(
      String substance, String reactionType, AllergySeverity severity, RecordStatus recordStatus) {}
}

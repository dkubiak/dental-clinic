package com.dentalclinic.patient.medicalhistory;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.time.LocalDate;
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
  private final MedicationEntryRepository medicationEntryRepository;
  private final ChronicConditionEntryRepository chronicConditionEntryRepository;
  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public MedicalHistoryService(
      AllergyEntryRepository allergyEntryRepository,
      MedicationEntryRepository medicationEntryRepository,
      ChronicConditionEntryRepository chronicConditionEntryRepository,
      PatientRecordRepository patientRecordRepository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.allergyEntryRepository = allergyEntryRepository;
    this.medicationEntryRepository = medicationEntryRepository;
    this.chronicConditionEntryRepository = chronicConditionEntryRepository;
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  private static final String ALLERGY_METADATA = "{\"entryType\":\"ALLERGY\"}";
  private static final String MEDICATION_METADATA = "{\"entryType\":\"MEDICATION\"}";
  private static final String CHRONIC_CONDITION_METADATA =
      "{\"entryType\":\"CHRONIC_CONDITION\"}";

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

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<MedicationEntry> getCurrentMedications(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<MedicationEntry> entries =
        medicationEntryRepository.findByPatientRecordIdAndRecordStatusOrderByCreatedAtDesc(
            patientId, RecordStatus.CURRENT);
    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_ENTRY_VIEWED,
        actorId,
        patientId,
        null,
        null,
        MEDICATION_METADATA);
    return entries;
  }

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<MedicationEntry> getMedicationHistory(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<MedicationEntry> entries =
        medicationEntryRepository.findByPatientRecordIdOrderByCreatedAtDesc(patientId);
    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_HISTORY_VIEWED,
        actorId,
        patientId,
        null,
        null,
        MEDICATION_METADATA);
    return entries;
  }

  /**
   * FR-010 — same correction semantics as {@link #addAllergy}.
   *
   * @throws PatientNotFoundException no patient record, or (if set) no superseded entry, exists.
   * @throws IllegalArgumentException {@code name}/{@code dosage} is blank (FR-011).
   */
  @Transactional
  public MedicationEntry addMedication(
      UUID patientId,
      String name,
      String dosage,
      LocalDate startDate,
      UUID supersedesEntryId,
      UUID actorId) {
    requirePatientExists(patientId);
    requireNonBlank(name, "name");
    requireNonBlank(dosage, "dosage");

    String beforeJson = null;
    if (supersedesEntryId != null) {
      MedicationEntry superseded =
          medicationEntryRepository
              .findById(supersedesEntryId)
              .orElseThrow(PatientNotFoundException::new);
      beforeJson = toJson(superseded);
      superseded.supersede();
      medicationEntryRepository.save(superseded);
    }

    MedicationEntry entry =
        new MedicationEntry(
            UUID.randomUUID(), patientId, name, dosage, startDate, supersedesEntryId, actorId);
    medicationEntryRepository.save(entry);

    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_ENTRY_ADDED,
        actorId,
        patientId,
        beforeJson,
        toJson(entry),
        MEDICATION_METADATA);
    return entry;
  }

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<ChronicConditionEntry> getCurrentChronicConditions(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<ChronicConditionEntry> entries =
        chronicConditionEntryRepository.findByPatientRecordIdAndRecordStatusOrderByCreatedAtDesc(
            patientId, RecordStatus.CURRENT);
    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_ENTRY_VIEWED,
        actorId,
        patientId,
        null,
        null,
        CHRONIC_CONDITION_METADATA);
    return entries;
  }

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<ChronicConditionEntry> getChronicConditionHistory(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<ChronicConditionEntry> entries =
        chronicConditionEntryRepository.findByPatientRecordIdOrderByCreatedAtDesc(patientId);
    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_HISTORY_VIEWED,
        actorId,
        patientId,
        null,
        null,
        CHRONIC_CONDITION_METADATA);
    return entries;
  }

  /**
   * FR-010 — same correction semantics as {@link #addAllergy}; {@code clinicalStatus} may also
   * change on a correction (Clarifications Session 2026-08-29 Q1) — that's an independent field,
   * not a second correction mechanism.
   *
   * @throws PatientNotFoundException no patient record, or (if set) no superseded entry, exists.
   * @throws IllegalArgumentException {@code name} is blank (FR-011).
   */
  @Transactional
  public ChronicConditionEntry addChronicCondition(
      UUID patientId,
      String name,
      ChronicConditionStatus clinicalStatus,
      LocalDate diagnosisDate,
      UUID supersedesEntryId,
      UUID actorId) {
    requirePatientExists(patientId);
    requireNonBlank(name, "name");

    String beforeJson = null;
    if (supersedesEntryId != null) {
      ChronicConditionEntry superseded =
          chronicConditionEntryRepository
              .findById(supersedesEntryId)
              .orElseThrow(PatientNotFoundException::new);
      beforeJson = toJson(superseded);
      superseded.supersede();
      chronicConditionEntryRepository.save(superseded);
    }

    ChronicConditionEntry entry =
        new ChronicConditionEntry(
            UUID.randomUUID(),
            patientId,
            name,
            clinicalStatus,
            diagnosisDate,
            supersedesEntryId,
            actorId);
    chronicConditionEntryRepository.save(entry);

    auditWriter.append(
        PatientAuditEventType.MEDICAL_HISTORY_ENTRY_ADDED,
        actorId,
        patientId,
        beforeJson,
        toJson(entry),
        CHRONIC_CONDITION_METADATA);
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

  private String toJson(MedicationEntry entry) {
    return objectMapper.writeValueAsString(
        new MedicationSnapshot(
            entry.getName(), entry.getDosage(), entry.getStartDate(), entry.getRecordStatus()));
  }

  private String toJson(ChronicConditionEntry entry) {
    return objectMapper.writeValueAsString(
        new ChronicConditionSnapshot(
            entry.getName(),
            entry.getClinicalStatus(),
            entry.getDiagnosisDate(),
            entry.getRecordStatus()));
  }

  private record AllergySnapshot(
      String substance, String reactionType, AllergySeverity severity, RecordStatus recordStatus) {}

  private record MedicationSnapshot(
      String name, String dosage, LocalDate startDate, RecordStatus recordStatus) {}

  private record ChronicConditionSnapshot(
      String name,
      ChronicConditionStatus clinicalStatus,
      LocalDate diagnosisDate,
      RecordStatus recordStatus) {}
}

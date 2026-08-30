package com.dentalclinic.patient.toothchart;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecord;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-022/FR-023/FR-030/FR-033/FR-036/FR-040/FR-041 — add/correct/close clinical findings. Mirrors
 * {@code MedicalHistoryService}'s shape (research.md D3): a "close" is just a correction whose only
 * semantic change is {@code clinicalStatus}/{@code resolvedDate} — both routed through the same
 * supersede-then-insert primitive.
 */
@Service
public class ToothFindingService {

  private final ToothFindingRepository toothFindingRepository;
  private final ToothPositionRepository toothPositionRepository;
  private final DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository;
  private final RootCanalRepository rootCanalRepository;
  private final ToothChartService toothChartService;
  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public ToothFindingService(
      ToothFindingRepository toothFindingRepository,
      ToothPositionRepository toothPositionRepository,
      DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository,
      RootCanalRepository rootCanalRepository,
      ToothChartService toothChartService,
      PatientRecordRepository patientRecordRepository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.toothFindingRepository = toothFindingRepository;
    this.toothPositionRepository = toothPositionRepository;
    this.diagnosisCatalogEntryRepository = diagnosisCatalogEntryRepository;
    this.rootCanalRepository = rootCanalRepository;
    this.toothChartService = toothChartService;
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * FR-022/FR-023/FR-036/FR-040/FR-041 — validates scope/surfaces/free-text/date/presence,
   * persists a new {@code CURRENT}/{@code ACTIVE} finding, audits {@code TOOTH_FINDING_ADDED} with
   * {@code before_state: null}.
   *
   * @throws PatientNotFoundException no patient, position, catalog entry, or (if set) canal exists.
   * @throws InvalidFindingException a validation rule above is violated.
   */
  @Transactional
  public ToothFinding addFinding(
      UUID patientId,
      int fdiNumber,
      UUID diagnosisCatalogEntryId,
      List<ToothSurface> surfaces,
      UUID rootCanalId,
      String severity,
      String freeTextDescription,
      String note,
      LocalDate diagnosisDate,
      UUID actorAccountId,
      FindingAuthorRole authorRole) {
    PatientRecord patient =
        patientRecordRepository.findById(patientId).orElseThrow(PatientNotFoundException::new);
    ToothPosition position = toothChartService.requirePosition(patientId, fdiNumber);
    DiagnosisCatalogEntry entry =
        diagnosisCatalogEntryRepository
            .findById(diagnosisCatalogEntryId)
            .orElseThrow(PatientNotFoundException::new);

    validateSurfaces(entry, surfaces);
    validateFreeText(entry, freeTextDescription);
    validateDiagnosisDate(patient, diagnosisDate);
    validatePresence(entry, position);
    validateRootCanal(position, rootCanalId);

    ToothFinding finding =
        new ToothFinding(
            UUID.randomUUID(),
            position.getId(),
            diagnosisCatalogEntryId,
            surfaces,
            rootCanalId,
            severity,
            freeTextDescription,
            note,
            diagnosisDate,
            FindingClinicalStatus.ACTIVE,
            null,
            null,
            actorAccountId,
            authorRole);
    toothFindingRepository.save(finding);

    auditWriter.append(
        PatientAuditEventType.TOOTH_FINDING_ADDED,
        actorAccountId,
        patientId,
        null,
        toJson(finding, fdiNumber),
        null);
    return finding;
  }

  /**
   * FR-032 — close/resolve a finding after treatment: research.md D3's supersede-then-insert, with
   * every field copied forward except {@code clinicalStatus = RESOLVED} and {@code resolvedDate}.
   *
   * @throws PatientNotFoundException no patient, or no finding with this id on this patient, exists.
   * @throws FindingConflictException the finding is already {@code SUPERSEDED}.
   */
  @Transactional
  public ToothFinding closeFinding(
      UUID patientId, UUID findingId, LocalDate resolvedDate, String note, UUID actorAccountId) {
    ToothFinding original = requireCurrentFinding(patientId, findingId);
    return supersede(
        patientId,
        original,
        original.getDiagnosisCatalogEntryId(),
        original.getSurfaces(),
        original.getRootCanalId(),
        original.getSeverity(),
        original.getFreeTextDescription(),
        note != null ? note : original.getNote(),
        original.getDiagnosisDate(),
        FindingClinicalStatus.RESOLVED,
        resolvedDate,
        actorAccountId,
        original.getAuthorRole());
  }

  /**
   * FR-033 — correct a previously-saved finding (e.g. a wrong surface): same supersede mechanism as
   * {@link #closeFinding}, with corrected field values instead of just a status flip. Never
   * overwrites or deletes the original (FR-030).
   *
   * @throws PatientNotFoundException no patient, finding, catalog entry, or (if set) canal exists.
   * @throws FindingConflictException the finding is already {@code SUPERSEDED}.
   * @throws InvalidFindingException a validation rule from {@link #addFinding} is violated.
   */
  @Transactional
  public ToothFinding correctFinding(
      UUID patientId,
      UUID findingId,
      UUID diagnosisCatalogEntryId,
      List<ToothSurface> surfaces,
      UUID rootCanalId,
      String severity,
      String freeTextDescription,
      String note,
      LocalDate diagnosisDate,
      UUID actorAccountId,
      FindingAuthorRole authorRole) {
    ToothFinding original = requireCurrentFinding(patientId, findingId);
    PatientRecord patient =
        patientRecordRepository.findById(patientId).orElseThrow(PatientNotFoundException::new);
    ToothPosition position =
        toothPositionRepository.findById(original.getToothPositionId()).orElseThrow(PatientNotFoundException::new);
    DiagnosisCatalogEntry entry =
        diagnosisCatalogEntryRepository
            .findById(diagnosisCatalogEntryId)
            .orElseThrow(PatientNotFoundException::new);

    validateSurfaces(entry, surfaces);
    validateFreeText(entry, freeTextDescription);
    validateDiagnosisDate(patient, diagnosisDate);
    validatePresence(entry, position);
    validateRootCanal(position, rootCanalId);

    return supersede(
        patientId,
        original,
        diagnosisCatalogEntryId,
        surfaces,
        rootCanalId,
        severity,
        freeTextDescription,
        note,
        diagnosisDate,
        original.getClinicalStatus(),
        original.getResolvedDate(),
        actorAccountId,
        authorRole);
  }

  /** research.md D3/D7 — the single supersede-then-insert primitive both close and correct use. */
  private ToothFinding supersede(
      UUID patientId,
      ToothFinding original,
      UUID diagnosisCatalogEntryId,
      List<ToothSurface> surfaces,
      UUID rootCanalId,
      String severity,
      String freeTextDescription,
      String note,
      LocalDate diagnosisDate,
      FindingClinicalStatus clinicalStatus,
      LocalDate resolvedDate,
      UUID actorAccountId,
      FindingAuthorRole authorRole) {
    if (original.getRecordStatus() != FindingRecordStatus.CURRENT) {
      throw new FindingConflictException(
          "This finding was already corrected or closed by someone else (FR-070/SC-010).");
    }
    int fdiNumber =
        toothPositionRepository.findById(original.getToothPositionId()).orElseThrow().getFdiNumber();

    String beforeJson = toJson(original, fdiNumber);
    original.supersede();
    toothFindingRepository.save(original);

    ToothFinding replacement =
        new ToothFinding(
            UUID.randomUUID(),
            original.getToothPositionId(),
            diagnosisCatalogEntryId,
            surfaces,
            rootCanalId,
            severity,
            freeTextDescription,
            note,
            diagnosisDate,
            clinicalStatus,
            resolvedDate,
            original.getId(),
            actorAccountId,
            authorRole);
    toothFindingRepository.save(replacement);

    auditWriter.append(
        PatientAuditEventType.TOOTH_FINDING_ADDED,
        actorAccountId,
        patientId,
        beforeJson,
        toJson(replacement, fdiNumber),
        null);
    return replacement;
  }

  /**
   * @throws PatientNotFoundException no patient, or no finding with this id on this patient, exists.
   */
  private ToothFinding requireCurrentFinding(UUID patientId, UUID findingId) {
    ToothChart chart = toothChartService.requireChart(patientId);
    ToothFinding finding =
        toothFindingRepository.findById(findingId).orElseThrow(PatientNotFoundException::new);
    ToothPosition position =
        toothPositionRepository
            .findById(finding.getToothPositionId())
            .orElseThrow(PatientNotFoundException::new);
    if (!position.getToothChartId().equals(chart.getId())) {
      throw new PatientNotFoundException();
    }
    return finding;
  }

  public int fdiNumberOf(ToothFinding finding) {
    return toothPositionRepository.findById(finding.getToothPositionId()).orElseThrow().getFdiNumber();
  }

  /**
   * @throws PatientNotFoundException no catalog entry with this id exists.
   */
  public DiagnosisCatalogEntry requireCatalogEntry(UUID diagnosisCatalogEntryId) {
    return diagnosisCatalogEntryRepository
        .findById(diagnosisCatalogEntryId)
        .orElseThrow(PatientNotFoundException::new);
  }

  private void validateSurfaces(DiagnosisCatalogEntry entry, List<ToothSurface> surfaces) {
    boolean surfaceScope = entry.getAnatomicalScope() == AnatomicalScope.SURFACE;
    boolean hasSurfaces = surfaces != null && !surfaces.isEmpty();
    if (surfaceScope && !hasSurfaces) {
      throw new InvalidFindingException(
          "At least one surface is required for a SURFACE-scope catalog entry (FR-022).");
    }
    if (!surfaceScope && hasSurfaces) {
      throw new InvalidFindingException(
          "Surfaces must not be supplied for a non-SURFACE-scope catalog entry (FR-023).");
    }
  }

  private void validateFreeText(DiagnosisCatalogEntry entry, String freeTextDescription) {
    if (entry.isRequiresFreeText() && (freeTextDescription == null || freeTextDescription.isBlank())) {
      throw new InvalidFindingException(
          "A free-text description is required for this catalog entry (FR-011a).");
    }
  }

  private void validateDiagnosisDate(PatientRecord patient, LocalDate diagnosisDate) {
    if (diagnosisDate == null) {
      throw new InvalidFindingException("diagnosisDate is required.");
    }
    if (diagnosisDate.isAfter(LocalDate.now())) {
      throw new InvalidFindingException("diagnosisDate must not be in the future (FR-036).");
    }
    if (diagnosisDate.isBefore(patient.getDateOfBirth())) {
      throw new InvalidFindingException(
          "diagnosisDate must not be before the patient's date of birth (FR-036).");
    }
  }

  private void validatePresence(DiagnosisCatalogEntry entry, ToothPosition position) {
    boolean missing = position.getPresence() != ToothPresence.PRESENT;
    boolean surfaceScope = entry.getAnatomicalScope() == AnatomicalScope.SURFACE;
    if (missing && surfaceScope && !entry.isAllowedForMissingTooth()) {
      throw new FindingConflictException(
          "Cannot add a SURFACE-scope finding to a position marked as missing (FR-040).");
    }
  }

  private void validateRootCanal(ToothPosition position, UUID rootCanalId) {
    if (rootCanalId == null) {
      return;
    }
    RootCanal canal =
        rootCanalRepository.findById(rootCanalId).orElseThrow(PatientNotFoundException::new);
    if (!canal.getToothPositionId().equals(position.getId()) || canal.isRemoved()) {
      throw new InvalidFindingException(
          "rootCanalId must reference a non-removed canal on this position (FR-067).");
    }
  }

  private String toJson(ToothFinding finding, int fdiNumber) {
    return objectMapper.writeValueAsString(
        new FindingSnapshot(
            fdiNumber,
            finding.getDiagnosisCatalogEntryId(),
            finding.getSurfaces(),
            finding.getSeverity(),
            finding.getClinicalStatus(),
            finding.getRecordStatus()));
  }

  private record FindingSnapshot(
      int fdiNumber,
      UUID diagnosisCatalogEntryId,
      List<ToothSurface> surfaces,
      String severity,
      FindingClinicalStatus clinicalStatus,
      FindingRecordStatus recordStatus) {}
}

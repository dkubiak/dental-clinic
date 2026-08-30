package com.dentalclinic.patient.toothchart;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * FR-065/FR-066/FR-068 — add/rename/change-state/soft-remove root canals. {@link RootCanal} is
 * mutable-in-place (research.md D4), never append-only — every mutating call carries an {@code
 * expectedVersion} (research.md D7, FR-070).
 */
@Service
public class RootCanalService {

  private static final int MAX_NON_REMOVED_CANALS = 6;

  private final RootCanalRepository rootCanalRepository;
  private final ToothChartService toothChartService;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public RootCanalService(
      RootCanalRepository rootCanalRepository,
      ToothChartService toothChartService,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.rootCanalRepository = rootCanalRepository;
    this.toothChartService = toothChartService;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * FR-065 — up to 6 non-removed canals per position, and only on a PRESENT position.
   *
   * @throws PatientNotFoundException no patient, or no position with this FDI number, exists.
   * @throws FindingConflictException the position already has 6 non-removed canals, or isn't
   *     PRESENT.
   */
  @Transactional
  public RootCanal addCanal(UUID patientId, int fdiNumber, String name, UUID actorId) {
    ToothPosition position = toothChartService.requirePosition(patientId, fdiNumber);
    if (position.getPresence() != ToothPresence.PRESENT) {
      throw new FindingConflictException(
          "Cannot add a root canal to a position that is not PRESENT.");
    }
    long nonRemovedCount =
        rootCanalRepository.findByToothPositionIdAndRemovedFalse(position.getId()).size();
    if (nonRemovedCount >= MAX_NON_REMOVED_CANALS) {
      throw new FindingConflictException(
          "A tooth position may have at most " + MAX_NON_REMOVED_CANALS + " non-removed root canals.");
    }

    RootCanal canal = new RootCanal(UUID.randomUUID(), position.getId(), name, actorId);
    rootCanalRepository.save(canal);

    auditWriter.append(
        PatientAuditEventType.ROOT_CANAL_ADDED,
        actorId,
        patientId,
        null,
        toJson(canal, fdiNumber),
        null);
    return canal;
  }

  /**
   * FR-065/FR-066 — rename and/or change treatment state.
   *
   * @throws PatientNotFoundException no patient, position, or canal (on this position) exists.
   * @throws FindingConflictException {@code expectedVersion} doesn't match the canal's current
   *     version.
   */
  @Transactional
  public RootCanal updateCanal(
      UUID patientId,
      int fdiNumber,
      UUID canalId,
      String name,
      RootCanalState state,
      int expectedVersion,
      UUID actorId) {
    RootCanal canal = requireCanal(patientId, fdiNumber, canalId);
    if (canal.getVersion() != expectedVersion) {
      throw new FindingConflictException(
          "This root canal was changed by someone else since it was read (FR-070/SC-010).");
    }
    String beforeJson = toJson(canal, fdiNumber);
    canal.update(name, state, actorId);
    rootCanalRepository.save(canal);

    auditWriter.append(
        PatientAuditEventType.ROOT_CANAL_CHANGED,
        actorId,
        patientId,
        beforeJson,
        toJson(canal, fdiNumber),
        null);
    return canal;
  }

  /**
   * FR-068 — soft delete only; findings that reference this canal keep referencing it (never
   * removed or hidden).
   *
   * @throws PatientNotFoundException no patient, position, or canal (on this position) exists.
   */
  @Transactional
  public void removeCanal(UUID patientId, int fdiNumber, UUID canalId, UUID actorId) {
    RootCanal canal = requireCanal(patientId, fdiNumber, canalId);
    String beforeJson = toJson(canal, fdiNumber);
    canal.remove(actorId);
    rootCanalRepository.save(canal);

    auditWriter.append(
        PatientAuditEventType.ROOT_CANAL_REMOVED,
        actorId,
        patientId,
        beforeJson,
        toJson(canal, fdiNumber),
        null);
  }

  private RootCanal requireCanal(UUID patientId, int fdiNumber, UUID canalId) {
    ToothPosition position = toothChartService.requirePosition(patientId, fdiNumber);
    RootCanal canal =
        rootCanalRepository.findById(canalId).orElseThrow(PatientNotFoundException::new);
    if (!canal.getToothPositionId().equals(position.getId())) {
      throw new PatientNotFoundException();
    }
    return canal;
  }

  private String toJson(RootCanal canal, int fdiNumber) {
    return objectMapper.writeValueAsString(
        new CanalSnapshot(
            fdiNumber, canal.getName(), canal.getState(), canal.isRemoved(), canal.getVersion()));
  }

  private record CanalSnapshot(
      int fdiNumber, String name, RootCanalState state, boolean removed, int version) {}
}

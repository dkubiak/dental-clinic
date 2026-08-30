package com.dentalclinic.patient.toothchart;

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
 * FR-005/FR-038/FR-044/FR-045 — read the full chart and its per-position history, and change
 * presence/dentition-mode; every read/write is audit-logged.
 */
@Service
public class ToothChartService {

  private final ToothChartRepository toothChartRepository;
  private final ToothPositionRepository toothPositionRepository;
  private final RootCanalRepository rootCanalRepository;
  private final ToothFindingRepository toothFindingRepository;
  private final DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository;
  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public ToothChartService(
      ToothChartRepository toothChartRepository,
      ToothPositionRepository toothPositionRepository,
      RootCanalRepository rootCanalRepository,
      ToothFindingRepository toothFindingRepository,
      DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository,
      PatientRecordRepository patientRecordRepository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.toothChartRepository = toothChartRepository;
    this.toothPositionRepository = toothPositionRepository;
    this.rootCanalRepository = rootCanalRepository;
    this.toothFindingRepository = toothFindingRepository;
    this.diagnosisCatalogEntryRepository = diagnosisCatalogEntryRepository;
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * FR-038/FR-070 — {@code @Version}-checked presence change (research.md D7); the resulting
   * incompatibility with existing SURFACE-scope findings is enforced by {@link
   * ToothFindingService#addFinding} at write time, not here (FR-040).
   *
   * @throws PatientNotFoundException no patient, or no position with this FDI number, exists.
   * @throws FindingConflictException {@code expectedVersion} doesn't match the position's current
   *     version.
   */
  @Transactional
  public PositionView changePresence(
      UUID patientId,
      int fdiNumber,
      ToothPresence presence,
      LocalDate presenceDate,
      int expectedVersion,
      UUID actorId) {
    requirePatientExists(patientId);
    ToothPosition position = requirePosition(patientId, fdiNumber);
    if (position.getVersion() != expectedVersion) {
      throw new FindingConflictException(
          "This position was changed by someone else since it was read (FR-070/SC-010).");
    }
    String beforeJson = toJson(position);
    position.changePresence(presence, presenceDate, actorId);
    toothPositionRepository.save(position);

    auditWriter.append(
        PatientAuditEventType.TOOTH_POSITION_PRESENCE_CHANGED,
        actorId,
        patientId,
        beforeJson,
        toJson(position),
        null);
    return toPositionView(position);
  }

  /**
   * FR-044/FR-045/FR-047 — override the (age-defaulted) dentition mode. {@link
   * ToothChart#changeDentitionMode} never touches any {@link ToothPosition}/{@link ToothFinding}
   * row — mode is a pure view filter over the 52 positions that always exist (research.md D2).
   *
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  @Transactional
  public ChartView changeDentitionMode(UUID patientId, DentitionMode dentitionMode, UUID actorId) {
    requirePatientExists(patientId);
    ToothChart chart = requireChart(patientId);
    String beforeJson = toJson(chart);
    chart.changeDentitionMode(dentitionMode, actorId);
    toothChartRepository.save(chart);

    auditWriter.append(
        PatientAuditEventType.DENTITION_MODE_CHANGED,
        actorId,
        patientId,
        beforeJson,
        toJson(chart),
        null);
    return buildChartView(chart);
  }

  private String toJson(ToothChart chart) {
    return objectMapper.writeValueAsString(new DentitionModeSnapshot(chart.getDentitionMode()));
  }

  private record DentitionModeSnapshot(DentitionMode dentitionMode) {}

  private String toJson(ToothPosition position) {
    return objectMapper.writeValueAsString(
        new PositionSnapshot(
            position.getFdiNumber(),
            position.getPresence(),
            position.getPresenceDate(),
            position.getVersion()));
  }

  private record PositionSnapshot(
      int fdiNumber, ToothPresence presence, LocalDate presenceDate, int version) {}

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public ChartView getChart(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    ToothChart chart = requireChart(patientId);
    ChartView view = buildChartView(chart);

    auditWriter.append(
        PatientAuditEventType.TOOTH_CHART_VIEWED, actorId, patientId, null, null, null);
    return view;
  }

  private ChartView buildChartView(ToothChart chart) {
    List<ToothPosition> positions =
        toothPositionRepository.findByToothChartIdOrderByFdiNumberAsc(chart.getId());
    List<PositionView> positionViews = positions.stream().map(this::toPositionView).toList();
    return new ChartView(chart, positionViews);
  }

  /**
   * FR-034 — full per-position history: current, resolved, and superseded findings, in
   * chronological order.
   *
   * @throws PatientNotFoundException no patient record, or no position with this FDI number,
   *     exists.
   */
  public List<FindingView> getPositionHistory(UUID patientId, int fdiNumber, UUID actorId) {
    requirePatientExists(patientId);
    ToothPosition position = requirePosition(patientId, fdiNumber);
    List<FindingView> history =
        toothFindingRepository.findByToothPositionIdOrderByCreatedAtAsc(position.getId()).stream()
            .map(this::toFindingView)
            .toList();
    auditWriter.append(
        PatientAuditEventType.TOOTH_CHART_VIEWED,
        actorId,
        patientId,
        null,
        null,
        "{\"detail\":\"position-history\"}");
    return history;
  }

  ToothChart requireChart(UUID patientId) {
    return toothChartRepository
        .findByPatientRecordId(patientId)
        .orElseThrow(PatientNotFoundException::new);
  }

  ToothPosition requirePosition(UUID patientId, int fdiNumber) {
    ToothChart chart = requireChart(patientId);
    return toothPositionRepository
        .findByToothChartIdAndFdiNumber(chart.getId(), fdiNumber)
        .orElseThrow(PatientNotFoundException::new);
  }

  void requirePatientExists(UUID patientId) {
    if (!patientRecordRepository.existsById(patientId)) {
      throw new PatientNotFoundException();
    }
  }

  private PositionView toPositionView(ToothPosition position) {
    List<RootCanal> canals = rootCanalRepository.findByToothPositionId(position.getId());
    List<FindingView> currentFindings =
        toothFindingRepository
            .findByToothPositionIdAndRecordStatus(position.getId(), FindingRecordStatus.CURRENT)
            .stream()
            .map(this::toFindingView)
            .toList();
    return new PositionView(position, canals, currentFindings);
  }

  FindingView toFindingView(ToothFinding finding) {
    DiagnosisCatalogEntry entry =
        diagnosisCatalogEntryRepository
            .findById(finding.getDiagnosisCatalogEntryId())
            .orElseThrow();
    return new FindingView(finding, entry);
  }

  public record ChartView(ToothChart chart, List<PositionView> positions) {}

  public record PositionView(
      ToothPosition position, List<RootCanal> canals, List<FindingView> currentFindings) {

    public int fdiNumber() {
      return position.getFdiNumber();
    }

    public DentitionType dentitionType() {
      return position.getDentitionType();
    }

    public ToothType toothType() {
      return position.getToothType();
    }

    public ToothPresence presence() {
      return position.getPresence();
    }
  }

  public record FindingView(ToothFinding finding, DiagnosisCatalogEntry entry) {}
}

package com.dentalclinic.patient.toothchart;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** FR-005/FR-038/FR-044/FR-045 — read the full chart and its per-position history, and change
 * presence/dentition-mode; every read/write is audit-logged. */
@Service
public class ToothChartService {

  private final ToothChartRepository toothChartRepository;
  private final ToothPositionRepository toothPositionRepository;
  private final RootCanalRepository rootCanalRepository;
  private final ToothFindingRepository toothFindingRepository;
  private final DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository;
  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;

  public ToothChartService(
      ToothChartRepository toothChartRepository,
      ToothPositionRepository toothPositionRepository,
      RootCanalRepository rootCanalRepository,
      ToothFindingRepository toothFindingRepository,
      DiagnosisCatalogEntryRepository diagnosisCatalogEntryRepository,
      PatientRecordRepository patientRecordRepository,
      PatientAuditWriter auditWriter) {
    this.toothChartRepository = toothChartRepository;
    this.toothPositionRepository = toothPositionRepository;
    this.rootCanalRepository = rootCanalRepository;
    this.toothFindingRepository = toothFindingRepository;
    this.diagnosisCatalogEntryRepository = diagnosisCatalogEntryRepository;
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
  }

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public ChartView getChart(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    ToothChart chart = requireChart(patientId);
    List<ToothPosition> positions =
        toothPositionRepository.findByToothChartIdOrderByFdiNumberAsc(chart.getId());
    List<PositionView> positionViews = positions.stream().map(this::toPositionView).toList();

    auditWriter.append(
        PatientAuditEventType.TOOTH_CHART_VIEWED, actorId, patientId, null, null, null);
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
    return toothChartRepository.findByPatientRecordId(patientId).orElseThrow(PatientNotFoundException::new);
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
        diagnosisCatalogEntryRepository.findById(finding.getDiagnosisCatalogEntryId()).orElseThrow();
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

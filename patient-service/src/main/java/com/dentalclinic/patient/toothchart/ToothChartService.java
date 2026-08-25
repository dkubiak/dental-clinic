package com.dentalclinic.patient.toothchart;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** FR-005/FR-006 — read the full chart, or toggle a single tooth's status; always audit-logged. */
@Service
public class ToothChartService {

  private final ToothStateRepository repository;
  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;
  private final ObjectMapper objectMapper;

  public ToothChartService(
      ToothStateRepository repository,
      PatientRecordRepository patientRecordRepository,
      PatientAuditWriter auditWriter,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
    this.objectMapper = objectMapper;
  }

  /**
   * @throws PatientNotFoundException no patient record with this id exists.
   */
  public List<ToothState> getChart(UUID patientId, UUID actorId) {
    requirePatientExists(patientId);
    List<ToothState> teeth = repository.findByPatientRecordIdOrderByToothNumberAsc(patientId);
    auditWriter.append(
        PatientAuditEventType.TOOTH_CHART_VIEWED, actorId, patientId, null, null, null);
    return teeth;
  }

  /**
   * @throws PatientNotFoundException no patient record, or no tooth with this number, exists.
   */
  @Transactional
  public ToothState setStatus(UUID patientId, int toothNumber, ToothStatus status, UUID actorId) {
    requirePatientExists(patientId);
    ToothState tooth =
        repository
            .findByPatientRecordIdAndToothNumber(patientId, toothNumber)
            .orElseThrow(PatientNotFoundException::new);

    String beforeJson = toJson(tooth);
    tooth.changeStatus(status, actorId);
    repository.save(tooth);

    auditWriter.append(
        PatientAuditEventType.TOOTH_STATE_CHANGED,
        actorId,
        patientId,
        beforeJson,
        toJson(tooth),
        null);
    return tooth;
  }

  private void requirePatientExists(UUID patientId) {
    if (!patientRecordRepository.existsById(patientId)) {
      throw new PatientNotFoundException();
    }
  }

  private String toJson(ToothState tooth) {
    return objectMapper.writeValueAsString(
        new ToothSnapshot(tooth.getToothNumber(), tooth.getStatus()));
  }

  private record ToothSnapshot(int toothNumber, ToothStatus status) {}
}

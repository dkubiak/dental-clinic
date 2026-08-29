package com.dentalclinic.patient.rodo;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.medicalhistory.AllergyEntry;
import com.dentalclinic.patient.medicalhistory.ChronicConditionEntry;
import com.dentalclinic.patient.medicalhistory.MedicalHistoryService;
import com.dentalclinic.patient.medicalhistory.MedicationEntry;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecord;
import com.dentalclinic.patient.record.PatientRecordRepository;
import com.dentalclinic.patient.toothchart.ToothState;
import com.dentalclinic.patient.toothchart.ToothStateRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * FR-009 — RODO subject-access export: full basic data + tooth chart (visit-history is always empty
 * in this version, US3), plus — since feature 004 — the full medical-history (current and
 * superseded, research.md #6 of 004: a subject-access request covers everything held, not just the
 * current view). DOCTOR only (research.md #6, rbac-policy.md rule 6 — deliberately not
 * ADMINISTRATOR).
 */
@Service
public class PatientExportService {

  private final PatientRecordRepository patientRecordRepository;
  private final ToothStateRepository toothStateRepository;
  private final MedicalHistoryService medicalHistoryService;
  private final PatientAuditWriter auditWriter;

  public PatientExportService(
      PatientRecordRepository patientRecordRepository,
      ToothStateRepository toothStateRepository,
      MedicalHistoryService medicalHistoryService,
      PatientAuditWriter auditWriter) {
    this.patientRecordRepository = patientRecordRepository;
    this.toothStateRepository = toothStateRepository;
    this.medicalHistoryService = medicalHistoryService;
    this.auditWriter = auditWriter;
  }

  /**
   * @throws PatientNotFoundException no record with this id exists.
   */
  public PatientExport export(UUID patientId, UUID actorId) {
    PatientRecord record =
        patientRecordRepository.findById(patientId).orElseThrow(PatientNotFoundException::new);
    List<ToothState> toothChart =
        toothStateRepository.findByPatientRecordIdOrderByToothNumberAsc(patientId);
    List<AllergyEntry> allergies = medicalHistoryService.getAllergyHistory(patientId, actorId);
    List<MedicationEntry> medications =
        medicalHistoryService.getMedicationHistory(patientId, actorId);
    List<ChronicConditionEntry> chronicConditions =
        medicalHistoryService.getChronicConditionHistory(patientId, actorId);

    auditWriter.append(
        PatientAuditEventType.PATIENT_DATA_EXPORTED, actorId, patientId, null, null, null);

    return new PatientExport(record, toothChart, allergies, medications, chronicConditions);
  }

  /** Visit history is deliberately excluded here — always empty in this version (US3). */
  public record PatientExport(
      PatientRecord patient,
      List<ToothState> toothChart,
      List<AllergyEntry> allergies,
      List<MedicationEntry> medications,
      List<ChronicConditionEntry> chronicConditions) {}
}

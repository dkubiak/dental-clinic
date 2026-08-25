package com.dentalclinic.patient.rodo;

import com.dentalclinic.patient.audit.PatientAuditEventType;
import com.dentalclinic.patient.audit.PatientAuditWriter;
import com.dentalclinic.patient.record.PatientNotFoundException;
import com.dentalclinic.patient.record.PatientRecordRepository;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * FR-010 — RODO erasure request. DOCTOR only (same rationale as {@link PatientExportService}).
 *
 * <p>TODO(T060): the actual anonymization/deletion execution — and the {@code
 * PATIENT_DATA_ERASURE_COMPLETED} event it would emit — is deliberately deferred to a follow-up
 * feature (plan.md Constitution Check, Principle II row): Polish medical-record retention periods
 * are statutorily defined per record type (years), so no single "done" completion point exists at
 * feature-implementation time. This is a reviewed, constitution-compliant exception, not a silent
 * gap.
 */
@Service
public class PatientErasureService {

  private final PatientRecordRepository patientRecordRepository;
  private final PatientAuditWriter auditWriter;

  public PatientErasureService(
      PatientRecordRepository patientRecordRepository, PatientAuditWriter auditWriter) {
    this.patientRecordRepository = patientRecordRepository;
    this.auditWriter = auditWriter;
  }

  /**
   * @throws PatientNotFoundException no record with this id exists.
   */
  public void requestErasure(UUID patientId, UUID actorId) {
    if (!patientRecordRepository.existsById(patientId)) {
      throw new PatientNotFoundException();
    }
    auditWriter.append(
        PatientAuditEventType.PATIENT_DATA_ERASURE_REQUESTED, actorId, patientId, null, null, null);
  }
}

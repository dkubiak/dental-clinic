package com.dentalclinic.patient.toothchart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Spec's "Stan uzębienia" (data-model.md ToothState) — one row per tooth per patient, pre-created
 * at PatientRecord creation time by {@link ToothChartInitializer} (research.md #3). {@code
 * updatedBy} stores a {@code staff_account.id} value but carries no DB-level FK, same cross-service
 * reasoning as {@code PatientRecord.createdBy}/{@code updatedBy} (research.md #5).
 */
@Entity
@Table(name = "tooth_state")
public class ToothState {

  @Id private UUID id;

  @Column(name = "patient_record_id", nullable = false)
  private UUID patientRecordId;

  @Column(name = "tooth_number", nullable = false)
  private short toothNumber; // FDI/ISO 3950 notation (FR-005)

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private ToothStatus status;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  protected ToothState() {
    // JPA
  }

  public ToothState(UUID id, UUID patientRecordId, int toothNumber) {
    this.id = id;
    this.patientRecordId = patientRecordId;
    this.toothNumber = (short) toothNumber;
    this.status = ToothStatus.HEALTHY;
    this.updatedAt = Instant.now();
  }

  /** FR-006 — always audit-logged by the caller (PatientAuditWriter). */
  public void changeStatus(ToothStatus status, UUID updatedBy) {
    this.status = status;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatientRecordId() {
    return patientRecordId;
  }

  public int getToothNumber() {
    return toothNumber;
  }

  public ToothStatus getStatus() {
    return status;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}

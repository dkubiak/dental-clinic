package com.dentalclinic.patient.medicalhistory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * data-model.md ChronicConditionEntry (FR-003) — name/clinicalStatus/diagnosisDate, plus the
 * shared {@code recordStatus} correction flag (FR-010). Two independent state machines on this one
 * entity (Clarifications Session 2026-08-29 Q1): {@code clinicalStatus} (ACTIVE ⇄ PAST, itself only
 * changed via a new correcting entry — there is no in-place clinical-status toggle either) is
 * orthogonal to {@code recordStatus} (CURRENT → SUPERSEDED, one-way, correction-only).
 */
@Entity
@Table(name = "chronic_condition_entry")
public class ChronicConditionEntry {

  @Id private UUID id;

  @Column(name = "patient_record_id", nullable = false)
  private UUID patientRecordId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "clinical_status", nullable = false)
  private ChronicConditionStatus clinicalStatus;

  @Column(name = "diagnosis_date", nullable = false)
  private LocalDate diagnosisDate;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "record_status", nullable = false)
  private RecordStatus recordStatus;

  @Column(name = "supersedes_entry_id")
  private UUID supersedesEntryId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  protected ChronicConditionEntry() {
    // JPA
  }

  public ChronicConditionEntry(
      UUID id,
      UUID patientRecordId,
      String name,
      ChronicConditionStatus clinicalStatus,
      LocalDate diagnosisDate,
      UUID supersedesEntryId,
      UUID createdBy) {
    this.id = id;
    this.patientRecordId = patientRecordId;
    this.name = name;
    this.clinicalStatus = clinicalStatus;
    this.diagnosisDate = diagnosisDate;
    this.recordStatus = RecordStatus.CURRENT;
    this.supersedesEntryId = supersedesEntryId;
    this.createdAt = Instant.now();
    this.createdBy = createdBy;
  }

  /** FR-010 — applied only as the side effect of a new correcting entry being inserted. */
  public void supersede() {
    this.recordStatus = RecordStatus.SUPERSEDED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatientRecordId() {
    return patientRecordId;
  }

  public String getName() {
    return name;
  }

  public ChronicConditionStatus getClinicalStatus() {
    return clinicalStatus;
  }

  public LocalDate getDiagnosisDate() {
    return diagnosisDate;
  }

  public RecordStatus getRecordStatus() {
    return recordStatus;
  }

  public UUID getSupersedesEntryId() {
    return supersedesEntryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}

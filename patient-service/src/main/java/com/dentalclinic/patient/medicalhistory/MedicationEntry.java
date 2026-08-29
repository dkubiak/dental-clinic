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
 * data-model.md MedicationEntry (FR-002) — name/dosage/startDate, plus the shared {@code
 * recordStatus} correction flag (FR-010). Append-only, same shape as {@link AllergyEntry}.
 */
@Entity
@Table(name = "medication_entry")
public class MedicationEntry {

  @Id private UUID id;

  @Column(name = "patient_record_id", nullable = false)
  private UUID patientRecordId;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false)
  private String dosage;

  @Column(name = "start_date", nullable = false)
  private LocalDate startDate;

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

  protected MedicationEntry() {
    // JPA
  }

  public MedicationEntry(
      UUID id,
      UUID patientRecordId,
      String name,
      String dosage,
      LocalDate startDate,
      UUID supersedesEntryId,
      UUID createdBy) {
    this.id = id;
    this.patientRecordId = patientRecordId;
    this.name = name;
    this.dosage = dosage;
    this.startDate = startDate;
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

  public String getDosage() {
    return dosage;
  }

  public LocalDate getStartDate() {
    return startDate;
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

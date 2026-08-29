package com.dentalclinic.patient.medicalhistory;

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
 * data-model.md AllergyEntry (FR-001) — substance/reaction/severity, plus the shared {@code
 * recordStatus} correction flag (FR-010). Append-only: {@link #supersede()} is the only mutation
 * ever applied to an existing row; every other field is fixed at construction time.
 */
@Entity
@Table(name = "allergy_entry")
public class AllergyEntry {

  @Id private UUID id;

  @Column(name = "patient_record_id", nullable = false)
  private UUID patientRecordId;

  @Column(nullable = false)
  private String substance;

  @Column(name = "reaction_type", nullable = false)
  private String reactionType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private AllergySeverity severity;

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

  protected AllergyEntry() {
    // JPA
  }

  public AllergyEntry(
      UUID id,
      UUID patientRecordId,
      String substance,
      String reactionType,
      AllergySeverity severity,
      UUID supersedesEntryId,
      UUID createdBy) {
    this.id = id;
    this.patientRecordId = patientRecordId;
    this.substance = substance;
    this.reactionType = reactionType;
    this.severity = severity;
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

  public String getSubstance() {
    return substance;
  }

  public String getReactionType() {
    return reactionType;
  }

  public AllergySeverity getSeverity() {
    return severity;
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

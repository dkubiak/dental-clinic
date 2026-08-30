package com.dentalclinic.patient.toothchart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * data-model.md ToothFinding — the append-only clinical entry (research.md D3). Immutable once
 * created except via the supersede operation: {@code recordStatus} flips {@code CURRENT ->
 * SUPERSEDED} only as the side effect of a new row being inserted with {@code
 * supersedesFindingId} pointing back at this one (FR-030/FR-033); {@code clinicalStatus} likewise
 * only ever changes via that same new row (a "close" is a correction whose only semantic change is
 * {@code clinicalStatus}/{@code resolvedDate}). No setters beyond {@link #supersede()} exist.
 */
@Entity
@Table(name = "tooth_finding")
public class ToothFinding {

  @Id private UUID id;

  @Column(name = "tooth_position_id", nullable = false)
  private UUID toothPositionId;

  @Column(name = "diagnosis_catalog_entry_id", nullable = false)
  private UUID diagnosisCatalogEntryId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column
  private String[] surfaces;

  @Column(name = "root_canal_id")
  private UUID rootCanalId;

  @Column
  private String severity;

  @Column(name = "free_text_description")
  private String freeTextDescription;

  @Column(length = 1000)
  private String note;

  @Column(name = "diagnosis_date", nullable = false)
  private LocalDate diagnosisDate;

  @Column(name = "resolved_date")
  private LocalDate resolvedDate;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "clinical_status", nullable = false)
  private FindingClinicalStatus clinicalStatus;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "record_status", nullable = false)
  private FindingRecordStatus recordStatus;

  @Column(name = "supersedes_finding_id")
  private UUID supersedesFindingId;

  @Column(name = "author_account_id", nullable = false)
  private UUID authorAccountId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "author_role", nullable = false)
  private FindingAuthorRole authorRole;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ToothFinding() {
    // JPA
  }

  public ToothFinding(
      UUID id,
      UUID toothPositionId,
      UUID diagnosisCatalogEntryId,
      List<ToothSurface> surfaces,
      UUID rootCanalId,
      String severity,
      String freeTextDescription,
      String note,
      LocalDate diagnosisDate,
      FindingClinicalStatus clinicalStatus,
      LocalDate resolvedDate,
      UUID supersedesFindingId,
      UUID authorAccountId,
      FindingAuthorRole authorRole) {
    this.id = id;
    this.toothPositionId = toothPositionId;
    this.diagnosisCatalogEntryId = diagnosisCatalogEntryId;
    this.surfaces =
        surfaces == null ? null : surfaces.stream().map(Enum::name).toArray(String[]::new);
    this.rootCanalId = rootCanalId;
    this.severity = severity;
    this.freeTextDescription = freeTextDescription;
    this.note = note;
    this.diagnosisDate = diagnosisDate;
    this.clinicalStatus = clinicalStatus;
    this.resolvedDate = resolvedDate;
    this.recordStatus = FindingRecordStatus.CURRENT;
    this.supersedesFindingId = supersedesFindingId;
    this.authorAccountId = authorAccountId;
    this.authorRole = authorRole;
    this.createdAt = Instant.now();
  }

  /** FR-033 — applied only as the side effect of a new correcting/closing finding being inserted. */
  public void supersede() {
    this.recordStatus = FindingRecordStatus.SUPERSEDED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getToothPositionId() {
    return toothPositionId;
  }

  public UUID getDiagnosisCatalogEntryId() {
    return diagnosisCatalogEntryId;
  }

  public List<ToothSurface> getSurfaces() {
    return surfaces == null ? null : Arrays.stream(surfaces).map(ToothSurface::valueOf).toList();
  }

  public UUID getRootCanalId() {
    return rootCanalId;
  }

  public String getSeverity() {
    return severity;
  }

  public String getFreeTextDescription() {
    return freeTextDescription;
  }

  public String getNote() {
    return note;
  }

  public LocalDate getDiagnosisDate() {
    return diagnosisDate;
  }

  public LocalDate getResolvedDate() {
    return resolvedDate;
  }

  public FindingClinicalStatus getClinicalStatus() {
    return clinicalStatus;
  }

  public FindingRecordStatus getRecordStatus() {
    return recordStatus;
  }

  public UUID getSupersedesFindingId() {
    return supersedesFindingId;
  }

  public UUID getAuthorAccountId() {
    return authorAccountId;
  }

  public FindingAuthorRole getAuthorRole() {
    return authorRole;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

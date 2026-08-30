package com.dentalclinic.patient.toothchart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * data-model.md ToothPosition — one of 52 rows per {@link ToothChart}, created once by {@link
 * ToothChartInitializer} and never deleted (research.md D2). Mutated in place (not append-only,
 * research.md D4) — only {@code presence}/{@code presenceDate} ever change, each change
 * optimistically locked via {@code @Version} (research.md D7, FR-070) and audit-logged with a
 * before/after snapshot.
 */
@Entity
@Table(name = "tooth_position")
public class ToothPosition {

  @Id private UUID id;

  @Column(name = "tooth_chart_id", nullable = false)
  private UUID toothChartId;

  @Column(name = "fdi_number", nullable = false)
  private short fdiNumber;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "dentition_type", nullable = false)
  private DentitionType dentitionType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "tooth_type", nullable = false)
  private ToothType toothType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private ToothPresence presence;

  @Column(name = "presence_date")
  private LocalDate presenceDate;

  @Version
  @Column(nullable = false)
  private int version;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  protected ToothPosition() {
    // JPA
  }

  public ToothPosition(
      UUID id, UUID toothChartId, int fdiNumber, DentitionType dentitionType, ToothType toothType) {
    this.id = id;
    this.toothChartId = toothChartId;
    this.fdiNumber = (short) fdiNumber;
    this.dentitionType = dentitionType;
    this.toothType = toothType;
    this.presence = ToothPresence.PRESENT;
    this.updatedAt = Instant.now();
  }

  /** FR-038 — always audit-logged by the caller (PatientAuditWriter). */
  public void changePresence(ToothPresence presence, LocalDate presenceDate, UUID updatedBy) {
    this.presence = presence;
    this.presenceDate = presenceDate;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getToothChartId() {
    return toothChartId;
  }

  public int getFdiNumber() {
    return fdiNumber;
  }

  public DentitionType getDentitionType() {
    return dentitionType;
  }

  public ToothType getToothType() {
    return toothType;
  }

  public ToothPresence getPresence() {
    return presence;
  }

  public LocalDate getPresenceDate() {
    return presenceDate;
  }

  public int getVersion() {
    return version;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}

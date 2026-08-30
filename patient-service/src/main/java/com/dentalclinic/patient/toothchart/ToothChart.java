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
 * data-model.md ToothChart — one row per {@code patient_record}, the aggregate root that does not
 * itself store any tooth state (research.md D2). {@code dentitionMode} is a pure view filter: all
 * 52 {@link ToothPosition} rows always exist regardless of this value.
 */
@Entity
@Table(name = "tooth_chart")
public class ToothChart {

  @Id private UUID id;

  @Column(name = "patient_record_id", nullable = false)
  private UUID patientRecordId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "dentition_mode", nullable = false)
  private DentitionMode dentitionMode;

  @Column(name = "dentition_mode_set_by")
  private UUID dentitionModeSetBy;

  @Column(name = "dentition_mode_set_at")
  private Instant dentitionModeSetAt;

  protected ToothChart() {
    // JPA
  }

  public ToothChart(UUID id, UUID patientRecordId, DentitionMode dentitionMode) {
    this.id = id;
    this.patientRecordId = patientRecordId;
    this.dentitionMode = dentitionMode;
  }

  /** FR-045/FR-047 — never touches any {@link ToothPosition}/{@code ToothFinding} row. */
  public void changeDentitionMode(DentitionMode dentitionMode, UUID setBy) {
    this.dentitionMode = dentitionMode;
    this.dentitionModeSetBy = setBy;
    this.dentitionModeSetAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getPatientRecordId() {
    return patientRecordId;
  }

  public DentitionMode getDentitionMode() {
    return dentitionMode;
  }

  public UUID getDentitionModeSetBy() {
    return dentitionModeSetBy;
  }

  public Instant getDentitionModeSetAt() {
    return dentitionModeSetAt;
  }
}

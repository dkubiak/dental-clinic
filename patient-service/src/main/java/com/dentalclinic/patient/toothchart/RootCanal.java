package com.dentalclinic.patient.toothchart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * data-model.md RootCanal — a small, mutable-in-place entity (research.md D4), not the
 * append-only correction machinery {@link ToothFinding} uses. Removing a canal only ever sets
 * {@code removed = true} (FR-068) — rows are never hard-deleted so findings that reference one
 * keep a valid, flaggable reference.
 */
@Entity
@Table(name = "root_canal")
public class RootCanal {

  @Id private UUID id;

  @Column(name = "tooth_position_id", nullable = false)
  private UUID toothPositionId;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private RootCanalState state;

  @Column(nullable = false)
  private boolean removed;

  @Version
  @Column(nullable = false)
  private int version;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  protected RootCanal() {
    // JPA
  }

  public RootCanal(UUID id, UUID toothPositionId, String name, UUID createdBy) {
    this.id = id;
    this.toothPositionId = toothPositionId;
    this.name = name;
    this.state = RootCanalState.NEEDS_TREATMENT;
    this.removed = false;
    this.createdAt = Instant.now();
    this.createdBy = createdBy;
    this.updatedAt = this.createdAt;
  }

  /** FR-065/FR-066 — rename and/or change treatment state, always audit-logged by the caller. */
  public void update(String name, RootCanalState state, UUID updatedBy) {
    if (name != null) {
      this.name = name;
    }
    if (state != null) {
      this.state = state;
    }
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  /** FR-068 — soft delete only; never removes the row. */
  public void remove(UUID updatedBy) {
    this.removed = true;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getToothPositionId() {
    return toothPositionId;
  }

  public String getName() {
    return name;
  }

  public RootCanalState getState() {
    return state;
  }

  public boolean isRemoved() {
    return removed;
  }

  public int getVersion() {
    return version;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public UUID getUpdatedBy() {
    return updatedBy;
  }
}

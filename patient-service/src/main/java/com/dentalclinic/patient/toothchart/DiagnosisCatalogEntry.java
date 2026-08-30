package com.dentalclinic.patient.toothchart;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * data-model.md DiagnosisCatalogEntry — Flyway-seeded reference data (research.md D5, V4
 * migration); read-only through the application, never written by {@code patient-service} code.
 * No setters exist — a row's shape only ever changes via a new migration.
 */
@Entity
@Table(name = "diagnosis_catalog_entry")
public class DiagnosisCatalogEntry {

  @Id private UUID id;

  @Column(nullable = false, unique = true)
  private String code;

  @Column(name = "name_pl", nullable = false)
  private String namePl;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private DiagnosisCategory category;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "anatomical_scope", nullable = false)
  private AnatomicalScope anatomicalScope;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private FindingLayer layer;

  @Column(name = "icd10_code")
  private String icd10Code;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "severity_options")
  private String[] severityOptions;

  @Column(name = "allowed_for_missing_tooth", nullable = false)
  private boolean allowedForMissingTooth;

  @Column(name = "deciduous_allowed", nullable = false)
  private boolean deciduousAllowed;

  @Column(name = "quick_access", nullable = false)
  private boolean quickAccess;

  @Column(name = "requires_free_text", nullable = false)
  private boolean requiresFreeText;

  @Column(name = "catalog_version", nullable = false)
  private int catalogVersion;

  protected DiagnosisCatalogEntry() {
    // JPA
  }

  public UUID getId() {
    return id;
  }

  public String getCode() {
    return code;
  }

  public String getNamePl() {
    return namePl;
  }

  public DiagnosisCategory getCategory() {
    return category;
  }

  public AnatomicalScope getAnatomicalScope() {
    return anatomicalScope;
  }

  public FindingLayer getLayer() {
    return layer;
  }

  public String getIcd10Code() {
    return icd10Code;
  }

  public String[] getSeverityOptions() {
    return severityOptions;
  }

  public boolean isAllowedForMissingTooth() {
    return allowedForMissingTooth;
  }

  public boolean isDeciduousAllowed() {
    return deciduousAllowed;
  }

  public boolean isQuickAccess() {
    return quickAccess;
  }

  public boolean isRequiresFreeText() {
    return requiresFreeText;
  }

  public int getCatalogVersion() {
    return catalogVersion;
  }
}

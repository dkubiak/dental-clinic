package com.dentalclinic.patient.record;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Spec's "Pacjent (kartoteka)" (data-model.md PatientRecord). {@code createdBy}/{@code updatedBy}
 * store a {@code staff_account.id} value but carry no DB-level FK — {@code staff_account} is owned
 * by the separate {@code auth-service}'s migration history (research.md #5); the id is trusted as
 * read out of the shared session ({@code SessionAuthenticationFilter}), never joined against.
 */
@Entity
@Table(name = "patient_record")
public class PatientRecord {

  @Id private UUID id;

  @Column(name = "first_name", nullable = false)
  private String firstName; // FR-001

  @Column(name = "last_name", nullable = false)
  private String lastName; // FR-001; indexed for search (FR-012)

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate
      dateOfBirth; // spec.md Assumptions — distinguishes patients once PESEL is optional

  // Unique when present (partial index, V1__patient_record.sql) — FR-002/FR-003.
  // @JdbcTypeCode(SqlTypes.CHAR) matches V1's CHAR(11) exactly — without it Hibernate's schema
  // validation (ddl-auto: validate) maps a plain String field to VARCHAR regardless of `length`,
  // and fails to start against the real CHAR(11) column (same pattern as AuditLogEntry's hash
  // columns in auth-service).
  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(name = "pesel", length = 11)
  private String pesel;

  @Column(name = "address_street", nullable = false)
  private String addressStreet;

  @Column(name = "address_building_no", nullable = false)
  private String addressBuildingNo;

  @Column(name = "address_postal_code", nullable = false)
  private String addressPostalCode;

  @Column(name = "address_city", nullable = false)
  private String addressCity;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "updated_by")
  private UUID updatedBy;

  protected PatientRecord() {
    // JPA
  }

  public PatientRecord(
      UUID id,
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String pesel,
      String addressStreet,
      String addressBuildingNo,
      String addressPostalCode,
      String addressCity,
      UUID createdBy) {
    this.id = id;
    this.firstName = firstName;
    this.lastName = lastName;
    this.dateOfBirth = dateOfBirth;
    this.pesel = pesel;
    this.addressStreet = addressStreet;
    this.addressBuildingNo = addressBuildingNo;
    this.addressPostalCode = addressPostalCode;
    this.addressCity = addressCity;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** FR-011 — edits basic data, always audit-logged by the caller (PatientAuditWriter). */
  public void updateBasicData(
      String firstName,
      String lastName,
      LocalDate dateOfBirth,
      String pesel,
      String addressStreet,
      String addressBuildingNo,
      String addressPostalCode,
      String addressCity,
      UUID updatedBy) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.dateOfBirth = dateOfBirth;
    this.pesel = pesel;
    this.addressStreet = addressStreet;
    this.addressBuildingNo = addressBuildingNo;
    this.addressPostalCode = addressPostalCode;
    this.addressCity = addressCity;
    this.updatedBy = updatedBy;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getPesel() {
    return pesel;
  }

  public String getAddressStreet() {
    return addressStreet;
  }

  public String getAddressBuildingNo() {
    return addressBuildingNo;
  }

  public String getAddressPostalCode() {
    return addressPostalCode;
  }

  public String getAddressCity() {
    return addressCity;
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

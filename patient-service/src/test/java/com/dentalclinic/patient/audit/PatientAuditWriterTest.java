package com.dentalclinic.patient.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T026 — {@code append()} from {@code patient-service} correctly continues an existing, pre-seeded
 * (auth-service-style) hash chain in the shared {@code audit_log_entry} table (research.md #5/#5a).
 * The table itself is owned by auth-service's migration history, so this test creates a
 * structurally-equivalent copy directly (mirroring V5__audit_log.sql +
 * V12__audit_log_entry_patient_target.sql, minus the {@code staff_account} FK constraints, which
 * are auth-service's own concern and not what this test is about) to simulate "auth-service's
 * migrations already ran against this shared database".
 */
class PatientAuditWriterTest extends PostgresIntegrationTestBase {

  @Autowired private PatientAuditWriter patientAuditWriter;

  // The table itself is created by PostgresIntegrationTestBase's own createSharedAuditLogTable()
  // (@BeforeEach, runs before this one) — this just isolates each test method from the others'
  // rows within that shared table.
  @BeforeEach
  void truncateAuditLog() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute("TRUNCATE audit_log_entry");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to truncate audit_log_entry", e);
    }
  }

  @Test
  void append_continuesExistingChainFromAuthService() throws SQLException {
    // A row "written by auth-service" (e.g. a login) — real content, real correctly-computed
    // hash, just inserted directly here since this test has no auth-service to write it for real.
    String seedHash = seedAuthServiceRow();

    patientAuditWriter.append(
        PatientAuditEventType.PATIENT_RECORD_CREATED,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        "{\"firstName\":\"Jan\"}",
        null);

    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement ps =
            connection.prepareStatement(
                "SELECT previous_entry_hash, entry_hash, target_patient_record_id,"
                    + " target_account_id FROM audit_log_entry ORDER BY id");
        ResultSet rs = ps.executeQuery()) {
      assertThat(rs.next()).isTrue(); // seed row
      String rowOneHash = rs.getString("entry_hash");
      assertThat(rowOneHash).isEqualTo(seedHash);

      assertThat(rs.next()).isTrue(); // patient-service's row
      assertThat(rs.getString("previous_entry_hash"))
          .as("new row must chain from the pre-existing auth-service row")
          .isEqualTo(rowOneHash);
      assertThat(rs.getObject("target_patient_record_id", UUID.class)).isNotNull();
      assertThat(rs.getObject("target_account_id", UUID.class))
          .as("patient-scoped events never populate target_account_id")
          .isNull();

      assertThat(rs.next()).isFalse(); // exactly two rows
    }
  }

  private String seedAuthServiceRow() throws SQLException {
    UUID actorAccountId = UUID.randomUUID();
    Instant occurredAt = Instant.now().minusSeconds(60);
    String hash =
        PatientAuditEntryHash.compute(
            null,
            PatientAuditEventType.PATIENT_RECORD_CREATED,
            actorAccountId,
            null,
            occurredAt,
            null,
            null);
    // Reuse PATIENT_RECORD_CREATED as a stand-in "auth-service event" — the enum value itself is
    // irrelevant to this test; only the hash-chain linkage across writers is under test.
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement ps =
            connection.prepareStatement(
                "INSERT INTO audit_log_entry (event_type, actor_account_id, occurred_at,"
                    + " previous_entry_hash, entry_hash) VALUES (?::audit_event_type, ?, ?, ?, ?)")) {
      ps.setString(1, "PATIENT_RECORD_CREATED");
      ps.setObject(2, actorAccountId);
      ps.setTimestamp(3, Timestamp.from(occurredAt));
      ps.setNull(4, java.sql.Types.CHAR);
      ps.setString(5, hash);
      ps.executeUpdate();
    }
    return hash;
  }
}

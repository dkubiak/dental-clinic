package com.dentalclinic.auth.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T085a — {@link AuditLogRetentionJob} purges rows older than 3 years (FR-018) and leaves recent
 * rows untouched. Manipulates {@code occurred_at} directly via JDBC (AuditLogWriter always stamps
 * {@code now()}) to simulate an old entry without waiting three years.
 */
class AuditLogRetentionJobTest extends PostgresIntegrationTestBase {

  @Autowired private AuditLogRetentionJob auditLogRetentionJob;
  @Autowired private AuditLogWriter auditLogWriter;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void purgesEntriesOlderThanMaxAge_leavingRecentEntriesIntact() {
    AuditLogEntry oldEntry =
        auditLogWriter.append(AuditEventType.LOGIN_SUCCESS, null, null, null, null, null);
    AuditLogEntry recentEntry =
        auditLogWriter.append(AuditEventType.LOGIN_SUCCESS, null, null, null, null, null);

    // auth_service_app (the primary JdbcTemplate's role) has UPDATE revoked on this table
    // (FR-008) — backdating occurred_at for this test's fixture setup needs the container's own
    // superuser connection, same as AuditLogImmutabilityIntegrationTest's direct-SQL checks.
    Instant fourYearsAgo = Instant.now().minus(4 * 365, ChronoUnit.DAYS);
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        PreparedStatement statement =
            connection.prepareStatement(
                "UPDATE audit_log_entry SET occurred_at = ? WHERE id = ?")) {
      statement.setTimestamp(1, java.sql.Timestamp.from(fourYearsAgo));
      statement.setLong(2, oldEntry.getId());
      statement.executeUpdate();
    } catch (java.sql.SQLException e) {
      throw new IllegalStateException(e);
    }

    auditLogRetentionJob.purgeExpiredEntries();

    List<Long> remainingIds =
        jdbcTemplate.queryForList("SELECT id FROM audit_log_entry", Long.class);
    assertThat(remainingIds).doesNotContain(oldEntry.getId()).contains(recentEntry.getId());
  }
}

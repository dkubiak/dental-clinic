package com.dentalclinic.auth.auditlog;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * T057 — direct SQL {@code UPDATE}/{@code DELETE} against {@code audit_log_entry}, issued as the
 * application's own DB role ({@code auth_service_app}), fails — proving the V5__audit_log.sql grant
 * restriction (FR-008) holds independent of any application-layer guard.
 */
class AuditLogImmutabilityIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private AuditLogWriter auditLogWriter;

  @Test
  void applicationRole_cannotUpdateOrDeleteAuditLogRows() throws Exception {
    AuditLogEntry entry =
        auditLogWriter.append(AuditEventType.LOGIN_SUCCESS, null, null, null, null, null);

    try (Connection connection =
        DriverManager.getConnection(POSTGRES.getJdbcUrl(), "auth_service_app", APP_ROLE_PASSWORD)) {
      assertThatThrownBy(
              () -> {
                try (Statement statement = connection.createStatement()) {
                  statement.execute(
                      "UPDATE audit_log_entry SET event_type = 'LOGIN_FAILURE' WHERE id = "
                          + entry.getId());
                }
              })
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");

      assertThatThrownBy(
              () -> {
                try (Statement statement = connection.createStatement()) {
                  statement.execute("DELETE FROM audit_log_entry WHERE id = " + entry.getId());
                }
              })
          .isInstanceOf(SQLException.class)
          .hasMessageContaining("permission denied");
    }
  }
}

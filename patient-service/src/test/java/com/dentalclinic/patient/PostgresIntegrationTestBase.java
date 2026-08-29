package com.dentalclinic.patient;

import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Mirrors {@code backend/src/test/java/com/dentalclinic/auth/support/PostgresIntegrationTestBase}
 * (a real PostgreSQL via Testcontainers, singleton-container pattern, never an in-memory substitute
 * — research.md #9 of 001). {@link #bootstrapAppRole()} exists for the same reason as that class's:
 * {@code V1__patient_record.sql} is what CREATEs the {@code patient_service_app} role that {@code
 * spring.datasource.username} defaults to, so Flyway (which must run V1 first) cannot authenticate
 * as that role yet on a fresh database — this pre-creates it (with a password, unlike V1's own
 * passwordless {@code CREATE ROLE IF NOT EXISTS}, which becomes a harmless no-op here) before
 * Spring Boot/Flyway ever starts.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTestBase {

  @Autowired protected MockMvc mockMvc;

  /**
   * Mirrors auth-service's own fixed double-submit CSRF cookie/header pair (see that class's
   * javadoc for why {@code SecurityMockMvcRequestPostProcessors.csrf()} is deliberately not used).
   */
  protected static final String CSRF_TOKEN_VALUE = "test-fixed-csrf-token";

  protected static final Cookie CSRF_TOKEN_COOKIE = new Cookie("XSRF-TOKEN", CSRF_TOKEN_VALUE);

  protected static final String APP_ROLE_PASSWORD = "test-app-password";

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("dental_clinic_auth");

  static {
    POSTGRES.start();
    bootstrapAppRole();
  }

  private static void bootstrapAppRole() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          DO $$
          BEGIN
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'patient_service_app') THEN
                  CREATE ROLE patient_service_app WITH LOGIN PASSWORD '%s' CREATEDB;
              END IF;
          END
          $$;
          """
              .formatted(APP_ROLE_PASSWORD));
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to bootstrap patient_service_app role for tests", e);
    }
  }

  // @BeforeEach, not part of the static container bootstrap: this must run AFTER Flyway (Spring
  // context startup) has already run V1/V2 against the empty schema, or Flyway sees a non-empty
  // "public" schema with no schema-history table yet and refuses to run (same reasoning as
  // SessionAuthenticationFilterTest's own spring_session bootstrap). `audit_log_entry` is owned by
  // auth-service's migration history, not patient-service's own — every test that exercises a
  // service touching PatientAuditWriter needs this structural copy of it to exist locally
  // (mirroring V5__audit_log.sql / V12__audit_log_entry_patient_target.sql, minus the
  // staff_account FK, which is auth-service's own concern). CREATE ... IF NOT EXISTS makes
  // re-running this before every test method harmless.
  @BeforeEach
  void createSharedAuditLogTable() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute(
          "DO $$ BEGIN IF NOT EXISTS (SELECT FROM pg_type WHERE typname ="
              + " 'audit_event_type') THEN CREATE TYPE audit_event_type AS ENUM"
              + " ('PATIENT_RECORD_CREATED', 'PATIENT_RECORD_UPDATED', 'PATIENT_RECORD_VIEWED',"
              + " 'TOOTH_STATE_CHANGED', 'TOOTH_CHART_VIEWED', 'PATIENT_DATA_EXPORTED',"
              + " 'PATIENT_DATA_ERASURE_REQUESTED', 'PATIENT_DATA_ERASURE_COMPLETED',"
              + " 'LOGIN_SUCCESS', 'MEDICAL_HISTORY_ENTRY_ADDED', 'MEDICAL_HISTORY_ENTRY_VIEWED',"
              + " 'MEDICAL_HISTORY_HISTORY_VIEWED'); END IF; END $$;");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS audit_log_entry (
              id                       BIGSERIAL PRIMARY KEY,
              event_type               audit_event_type NOT NULL,
              actor_account_id         UUID,
              target_account_id        UUID,
              target_patient_record_id UUID,
              occurred_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
              before_state             JSONB,
              after_state              JSONB,
              metadata                 JSONB,
              previous_entry_hash      CHAR(64),
              entry_hash               CHAR(64) NOT NULL
          )
          """);
      statement.execute("GRANT SELECT, INSERT ON audit_log_entry TO patient_service_app");
      statement.execute(
          "GRANT USAGE, SELECT ON SEQUENCE audit_log_entry_id_seq TO patient_service_app");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to create shared audit_log_entry table", e);
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    // Flyway runs as the container's own admin user (owns/creates every table, V1 onward).
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);

    // The application itself connects as the least-privilege role V1 grants.
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "patient_service_app");
    registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
  }
}

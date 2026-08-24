package com.dentalclinic.patient;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
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

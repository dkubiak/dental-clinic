package com.dentalclinic.patient.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextImpl;

/**
 * T019/T020 — {@code patient-service} authenticates a request by reading the *same* Spring Session
 * JDBC tables {@code auth-service} writes (research.md #7), never issuing sessions of its own.
 * Since those tables (owned by auth-service's own migration history, V3__session.sql) don't exist
 * in this service's own isolated Testcontainers database, this test creates them directly
 * (mirroring V3's DDL) to simulate "auth-service's migrations already ran against this shared
 * database", then seeds a session row exactly as {@code SessionEstablisher} (001) would — a {@link
 * UsernamePasswordAuthenticationToken} whose principal is the account id (as a string) and whose
 * sole authority is {@code ROLE_<role>}.
 */
class SessionAuthenticationFilterTest extends PostgresIntegrationTestBase {

  // @BeforeEach, not @BeforeAll: this must run AFTER Flyway (Spring context startup) has
  // already run V1/V2 against the empty schema — creating these tables any earlier makes
  // Flyway see a non-empty "public" schema with no schema-history table yet and refuse to run.
  // CREATE ... IF NOT EXISTS makes re-running this before every test method harmless.
  @BeforeEach
  void createSharedSessionTables() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      // Mirrors backend/src/main/resources/db/migration/V3__session.sql exactly.
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS spring_session (
              primary_id            CHAR(36) NOT NULL,
              session_id            CHAR(36) NOT NULL,
              creation_time         BIGINT NOT NULL,
              last_access_time      BIGINT NOT NULL,
              max_inactive_interval INT NOT NULL,
              expiry_time           BIGINT NOT NULL,
              principal_name        VARCHAR(100),
              CONSTRAINT spring_session_pk PRIMARY KEY (primary_id)
          )
          """);
      statement.execute(
          "CREATE UNIQUE INDEX IF NOT EXISTS spring_session_ix1 ON spring_session (session_id)");
      statement.execute(
          """
          CREATE TABLE IF NOT EXISTS spring_session_attributes (
              session_primary_id CHAR(36) NOT NULL,
              attribute_name      VARCHAR(200) NOT NULL,
              attribute_bytes      BYTEA NOT NULL,
              CONSTRAINT spring_session_attributes_pk PRIMARY KEY (session_primary_id, attribute_name),
              CONSTRAINT spring_session_attributes_fk FOREIGN KEY (session_primary_id)
                  REFERENCES spring_session (primary_id) ON DELETE CASCADE
          )
          """);
      // Mirrors V12__audit_log_entry_patient_target.sql's grant (T011) — SELECT, UPDATE only.
      statement.execute(
          "GRANT SELECT, UPDATE ON spring_session, spring_session_attributes TO"
              + " patient_service_app");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to create shared spring_session tables", e);
    }
  }

  @Test
  void validSessionForDoctorPrincipal_allowsAccessToProtectedEndpoint() throws Exception {
    UUID accountId = UUID.randomUUID();
    String sessionId = seedSession(accountId, "DOCTOR", Instant.now().plusSeconds(900));

    mockMvc
        .perform(get("/test-support/authenticated").cookie(sessionCookie(sessionId)))
        .andExpect(status().isOk());
  }

  @Test
  void noSessionCookie_isRejected() throws Exception {
    mockMvc.perform(get("/test-support/authenticated")).andExpect(status().isUnauthorized());
  }

  @Test
  void unknownSessionId_isRejected() throws Exception {
    mockMvc
        .perform(
            get("/test-support/authenticated").cookie(sessionCookie(UUID.randomUUID().toString())))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredSession_isRejected() throws Exception {
    UUID accountId = UUID.randomUUID();
    String sessionId = seedSession(accountId, "DOCTOR", Instant.now().minusSeconds(60));

    mockMvc
        .perform(get("/test-support/authenticated").cookie(sessionCookie(sessionId)))
        .andExpect(status().isUnauthorized());
  }

  private Cookie sessionCookie(String sessionId) {
    String cookieValue =
        Base64.getEncoder()
            .encodeToString(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return new Cookie("SESSION", cookieValue);
  }

  /** Seeds a session row exactly as auth-service's SessionEstablisher (001) would. */
  private String seedSession(UUID accountId, String role, Instant expiresAt) throws SQLException {
    String primaryId = UUID.randomUUID().toString();
    String sessionId = UUID.randomUUID().toString();
    byte[] securityContextBytes = serializeSecurityContext(accountId, role);

    try (Connection connection =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      try (var insertSession =
          connection.prepareStatement(
              """
              INSERT INTO spring_session
                (primary_id, session_id, creation_time, last_access_time,
                 max_inactive_interval, expiry_time, principal_name)
              VALUES (?, ?, ?, ?, ?, ?, ?)
              """)) {
        long now = System.currentTimeMillis();
        insertSession.setString(1, primaryId);
        insertSession.setString(2, sessionId);
        insertSession.setLong(3, now);
        insertSession.setLong(4, now);
        insertSession.setInt(5, 900);
        insertSession.setLong(6, expiresAt.toEpochMilli());
        insertSession.setString(7, accountId.toString());
        insertSession.executeUpdate();
      }
      try (var insertAttribute =
          connection.prepareStatement(
              """
              INSERT INTO spring_session_attributes (session_primary_id, attribute_name, attribute_bytes)
              VALUES (?, ?, ?)
              """)) {
        insertAttribute.setString(1, primaryId);
        insertAttribute.setString(2, "SPRING_SECURITY_CONTEXT");
        insertAttribute.setBytes(3, securityContextBytes);
        insertAttribute.executeUpdate();
      }
    }
    return sessionId;
  }

  private byte[] serializeSecurityContext(UUID accountId, String role) {
    Authentication authentication =
        new UsernamePasswordAuthenticationToken(
            accountId.toString(), null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    SecurityContext context = new SecurityContextImpl(authentication);
    try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos)) {
      oos.writeObject(context);
      return bos.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }
}

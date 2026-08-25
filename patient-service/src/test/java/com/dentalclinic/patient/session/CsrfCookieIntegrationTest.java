package com.dentalclinic.patient.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.patient.PostgresIntegrationTestBase;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
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
 * Regression test mirroring backend/.../config/CsrfCookieIntegrationTest (001) — the same gap
 * exists here: without forcing Spring Security's deferred CSRF token to resolve, the {@code
 * XSRF-TOKEN} cookie is never actually written to the response for a pure JSON REST API, so
 * Angular's built-in XSRF interceptor has nothing to echo back as {@code X-XSRF-TOKEN} and every
 * real mutating request from the browser fails (discovered as a live gap while running
 * 002-patient-records' quickstart validation, T063).
 *
 * <p>Deliberately seeds a real {@code spring_session} row and drives requests through the actual
 * {@link SessionAuthenticationFilter} (mirroring {@code SessionAuthenticationFilterTest}'s pattern)
 * rather than MockMvc's {@code SecurityMockMvcRequestPostProcessors.user()} shortcut: {@code
 * user()} establishes the {@code Authentication} before the filter chain runs, so it never triggers
 * Spring Security's "new authentication happened during this request" detection the way a real
 * request does. That distinction matters here because it's exactly what surfaces a second, deeper
 * bug this test also guards against — see {@code
 * aSecondRequestOnTheSameSession_doesNotHaveItsXsrfTokenCookieSilentlyDeleted} below.
 */
class CsrfCookieIntegrationTest extends PostgresIntegrationTestBase {

  @BeforeEach
  void createSharedSessionTables() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        var statement = connection.createStatement()) {
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
      statement.execute(
          "GRANT SELECT, UPDATE ON spring_session, spring_session_attributes TO"
              + " patient_service_app");
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to create shared spring_session tables", e);
    }
  }

  @Test
  void authenticatedResponse_carriesAWorkingXsrfTokenCookie_forARealMutatingRequest()
      throws Exception {
    UUID accountId = UUID.randomUUID();
    Cookie sessionCookie = sessionCookie(seedSession(accountId, "RECEPTION"));

    var getResult =
        mockMvc
            .perform(get("/patients?q=Kowal").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andReturn();
    Cookie xsrfCookie = getResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookie)
        .as("patient-service should also write an XSRF-TOKEN cookie, same as auth-service")
        .isNotNull();

    // No .with(csrf()) / fixed-cookie bypass here — this is the exact mechanism the real Angular
    // frontend uses: read the cookie a prior response set, echo it back as a header.
    mockMvc
        .perform(
            post("/patients")
                .cookie(sessionCookie, xsrfCookie)
                .header("X-XSRF-TOKEN", xsrfCookie.getValue())
                .contentType(APPLICATION_JSON)
                .content(
                    """
                    {"firstName":"Csrf","lastName":"CookieTest","dateOfBirth":"1990-01-15",
                     "addressStreet":"Polna","addressBuildingNo":"1","addressPostalCode":"00-001",
                     "addressCity":"Warszawa"}
                    """))
        .andExpect(status().isCreated());
  }

  @Test
  void aSecondRequestOnTheSameSession_doesNotHaveItsXsrfTokenCookieSilentlyDeleted()
      throws Exception {
    // T063 finding: SessionAuthenticationFilter authenticates fresh from JDBC on every single
    // request (by design — this service never persists its own SecurityContext). Spring
    // Security's SessionManagementFilter can't tell that apart from "the user just logged in
    // this request", so its default CsrfAuthenticationStrategy fires on every request and
    // deletes any XSRF-TOKEN cookie it finds already present — breaking every request after the
    // first. SecurityConfig must disable that strategy (NullAuthenticatedSessionStrategy) for
    // this to pass; SecurityMockMvcRequestPostProcessors.user() would NOT have caught this, since
    // it establishes the Authentication before the filter chain runs rather than mid-chain like
    // the real SessionAuthenticationFilter does.
    UUID accountId = UUID.randomUUID();
    Cookie sessionCookie = sessionCookie(seedSession(accountId, "RECEPTION"));

    var firstResult =
        mockMvc
            .perform(get("/patients?q=Kowal").cookie(sessionCookie))
            .andExpect(status().isOk())
            .andReturn();
    Cookie xsrfCookie = firstResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookie).isNotNull();

    var secondResult =
        mockMvc
            .perform(get("/patients?q=Nowak").cookie(sessionCookie, xsrfCookie))
            .andExpect(status().isOk())
            .andReturn();
    Cookie xsrfCookieAfterSecondRequest = secondResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookieAfterSecondRequest)
        .as(
            "a second request on the same still-valid session must not silently delete the"
                + " CSRF cookie — that leaves the browser with no valid token for its next"
                + " mutating request")
        .satisfiesAnyOf(
            cookie -> assertThat(cookie).isNull(), // untouched is fine
            cookie -> assertThat(cookie.getValue()).isNotBlank() // reissued is fine too
            );
  }

  private Cookie sessionCookie(String sessionId) {
    String cookieValue =
        Base64.getEncoder()
            .encodeToString(sessionId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    return new Cookie("SESSION", cookieValue);
  }

  private String seedSession(UUID accountId, String role) throws SQLException {
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
        insertSession.setLong(6, Instant.now().plusSeconds(900).toEpochMilli());
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

package com.dentalclinic.auth.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.config.SessionHardCapFilter;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T039b — session is invalidated at the 8-hour hard cap even when kept continuously active (FR-012,
 * spec Assumptions). Simulates the passage of time by directly overwriting the serialized {@link
 * SessionHardCapFilter#AUTHENTICATED_AT_ATTRIBUTE} session attribute in Spring Session JDBC's
 * backing table with a value from 9 hours ago — {@code JdbcIndexedSessionRepository} serializes
 * attributes via Spring's standard {@code SerializingConverter} (plain JDK serialization), which
 * {@link #serialize} reproduces exactly.
 */
class SessionHardCapIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void sessionInvalidatedAtHardCap_evenIfKeptContinuouslyActive() throws Exception {
    String email = "hard-cap-test@dentalclinic.example";
    String password = "correct-horse-battery";
    var account = testAccountFactory.createActiveAccount(email, password, Role.ADMINISTRATOR);
    String secret = testAccountFactory.enrollMfa(account);

    Cookie sessionCookie =
        loginAndGetSessionCookie(
            email, password, "10.0.5.1", () -> testAccountFactory.currentTotpCode(secret));

    // Sanity check: the session is valid immediately after establishment ("kept active" so far).
    mockMvc
        .perform(get("/test-support/admin-only").cookie(sessionCookie))
        .andExpect(status().isOk());

    byte[] staleAuthenticatedAt = serialize(Instant.now().minus(9, ChronoUnit.HOURS));
    String sessionId = decodeSessionId(sessionCookie);
    int updated =
        jdbcTemplate.update(
            """
            UPDATE spring_session_attributes
            SET attribute_bytes = ?
            WHERE session_primary_id = (SELECT primary_id FROM spring_session WHERE session_id = ?)
              AND attribute_name = ?
            """,
            staleAuthenticatedAt,
            sessionId,
            SessionHardCapFilter.AUTHENTICATED_AT_ATTRIBUTE);
    if (updated != 1) {
      throw new IllegalStateException(
          "Expected exactly one authenticatedAt session attribute row, updated " + updated);
    }

    mockMvc
        .perform(get("/test-support/admin-only").cookie(sessionCookie))
        .andExpect(status().isUnauthorized());
  }

  private static byte[] serialize(Object value) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    try (ObjectOutputStream objectStream = new ObjectOutputStream(byteStream)) {
      objectStream.writeObject(value);
    }
    return byteStream.toByteArray();
  }
}

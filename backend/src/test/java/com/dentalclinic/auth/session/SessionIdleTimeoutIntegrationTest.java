package com.dentalclinic.auth.session;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T039a — session expires after 15 minutes of inactivity; a subsequent request with the expired
 * session id returns 401 (FR-012). Simulates the passage of idle time by directly moving the Spring
 * Session JDBC row's {@code last_access_time} into the past (see V3__session.sql / SessionConfig,
 * T027) rather than waiting 15 real minutes. {@code JdbcIndexedSessionRepository.findById} treats
 * an expired session as absent without needing its cleanup task to have run first — but its {@code
 * MapSession.isExpired(Instant)} check recomputes expiry from {@code last_access_time} + {@code
 * max_inactive_interval} freshly on every read; it does NOT consult the stored {@code expiry_time}
 * column (that column exists only for the separate scheduled cleanup query), so {@code
 * last_access_time} is the column that actually needs to move.
 */
class SessionIdleTimeoutIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void requestAfterIdleExpiry_returns401() throws Exception {
    String email = "idle-timeout-test@dentalclinic.example";
    String password = "correct-horse-battery";
    var account = testAccountFactory.createActiveAccount(email, password, Role.ADMINISTRATOR);
    String secret = testAccountFactory.enrollMfa(account);

    Cookie sessionCookie =
        loginAndGetSessionCookie(
            email, password, "10.0.4.1", () -> testAccountFactory.currentTotpCode(secret));

    // Sanity check: the session is valid immediately after establishment.
    mockMvc
        .perform(get("/test-support/admin-only").cookie(sessionCookie))
        .andExpect(status().isOk());

    String sessionId = decodeSessionId(sessionCookie);
    jdbcTemplate.update(
        "UPDATE spring_session SET last_access_time = ? WHERE session_id = ?",
        Timestamp.from(Instant.now().minus(16, java.time.temporal.ChronoUnit.MINUTES)).getTime(),
        sessionId);

    mockMvc
        .perform(get("/test-support/admin-only").cookie(sessionCookie))
        .andExpect(status().isUnauthorized());
  }
}

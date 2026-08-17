package com.dentalclinic.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T069 — deactivating an account immediately invalidates its active session (Edge Cases, spec.md):
 * the {@code spring_session} row for a real, logged-in session is gone right after {@code POST
 * /accounts/{id}/deactivate}, not merely after the session's own idle/hard-cap expiry.
 */
class DeactivateSessionInvalidationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void deactivatingAnAccount_deletesItsActiveSpringSessionRow() throws Exception {
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "deactivate-session-invalidation@dentalclinic.example",
            "irrelevant-password-1",
            Role.RECEPTION);
    String secret = testAccountFactory.enrollMfa(target);
    StaffAccount admin =
        testAccountFactory.createActiveAccount(
            "deactivate-session-invalidation-admin@dentalclinic.example",
            "irrelevant-password-1",
            Role.ADMINISTRATOR);

    Cookie sessionCookie =
        loginAndGetSessionCookie(
            target.getEmail(),
            "irrelevant-password-1",
            "10.30.40.51",
            () -> testAccountFactory.currentTotpCode(secret));
    String sessionId = decodeSessionId(sessionCookie);

    Long countBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE session_id = ?", Long.class, sessionId);
    assertThat(countBefore).isEqualTo(1L);

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/deactivate")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE))
        .andReturn();

    Long countAfter =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE session_id = ?", Long.class, sessionId);
    assertThat(countAfter).isEqualTo(0L);
  }
}

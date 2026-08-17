package com.dentalclinic.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * T069a — changing an account's role immediately invalidates its active session(s) (FR-007a; Edge
 * Cases, spec.md), so the new role takes effect on the very next request rather than only after the
 * old session naturally expires.
 */
class RoleChangeSessionInvalidationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void changingRole_deletesTheAccountsActiveSpringSessionRow() throws Exception {
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "role-change-session-invalidation@dentalclinic.example",
            "irrelevant-password-1",
            Role.RECEPTION);
    String secret = testAccountFactory.enrollMfa(target);
    StaffAccount admin =
        testAccountFactory.createActiveAccount(
            "role-change-session-invalidation-admin@dentalclinic.example",
            "irrelevant-password-1",
            Role.ADMINISTRATOR);

    Cookie sessionCookie =
        loginAndGetSessionCookie(
            target.getEmail(),
            "irrelevant-password-1",
            "10.30.40.61",
            () -> testAccountFactory.currentTotpCode(secret));
    String sessionId = decodeSessionId(sessionCookie);

    Long countBefore =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE session_id = ?", Long.class, sessionId);
    assertThat(countBefore).isEqualTo(1L);

    mockMvc
        .perform(
            patch("/accounts/" + target.getId())
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"role\":\"DOCTOR\"}"))
        .andReturn();

    Long countAfter =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM spring_session WHERE session_id = ?", Long.class, sessionId);
    assertThat(countAfter).isEqualTo(0L);
  }
}

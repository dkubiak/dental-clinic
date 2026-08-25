package com.dentalclinic.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * {@code GET /auth/session} — lets the frontend rehydrate {@code AuthState.currentRole} after a
 * full page reload/deep link, where the in-memory role set right after login/MFA is otherwise lost
 * even though the session cookie itself is still valid. Discovered as a live gap while running
 * 002-patient-records' quickstart validation (T063): every {@code roleGuard}-protected route
 * redirected an already-authenticated user back to {@code /login} on any page reload, since nothing
 * ever re-populated {@code AuthState} from the still-valid session. {@code 200} + the caller's role
 * for a valid session, {@code 401} for no/invalid session (same {@code
 * .anyRequest().authenticated()} boundary as every other endpoint, SecurityConfig).
 */
class SessionInfoIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  @Test
  void validSession_returns200WithRole() throws Exception {
    String email = "session-info-test@dentalclinic.example";
    String password = "correct-horse-battery";
    var account = testAccountFactory.createActiveAccount(email, password, Role.DOCTOR);
    String secret = testAccountFactory.enrollMfa(account);

    Cookie sessionCookie =
        loginAndGetSessionCookie(
            email, password, "10.60.70.1", () -> testAccountFactory.currentTotpCode(secret));

    mockMvc
        .perform(get("/auth/session").cookie(sessionCookie))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("DOCTOR"));
  }

  @Test
  void noSession_returns401() throws Exception {
    mockMvc.perform(get("/auth/session")).andExpect(status().isUnauthorized());
  }
}

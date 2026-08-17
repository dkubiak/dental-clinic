package com.dentalclinic.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.StaffAccount;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Regression test for {@link CsrfCookieFilter}. Without it, Spring Security's deferred CSRF token
 * is never actually resolved for a pure JSON REST API (no server-rendered view ever reads {@code
 * ${_csrf.token}}), so the {@code XSRF-TOKEN} cookie is never sent to the browser — and Angular's
 * built-in XSRF interceptor (which reads that cookie and echoes it back as {@code X-XSRF-TOKEN})
 * then has nothing to send, so every mutating request from a real browser would 403 (confirmed
 * against the real packaged app, not just this test). {@code user(...)} (rather than a full real
 * login/MFA flow) is used here deliberately to isolate the CSRF-cookie mechanism itself from the
 * rest of the login machinery — the RBAC/session pieces are already covered elsewhere (e.g.
 * AccountControllerContractTest). See PostgresIntegrationTestBase#CSRF_TOKEN_COOKIE's javadoc for
 * why every mutating-request test in this codebase uses that fixed cookie/header pair instead of
 * {@code SecurityMockMvcRequestPostProcessors.csrf()}.
 */
class CsrfCookieIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  @Test
  void authenticatedResponse_carriesAWorkingXsrfTokenCookie_forARealMutatingRequest()
      throws Exception {
    StaffAccount admin =
        testAccountFactory.createActiveAccount(
            "csrf-cookie-admin@dentalclinic.example", "irrelevant-password-1", Role.ADMINISTRATOR);
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "csrf-cookie-target@dentalclinic.example", "irrelevant-password-1", Role.RECEPTION);

    // GET /audit-log (paginated, T063) rather than GET /accounts: the latter is unpaginated and
    // its response grows across this shared-container test run (PostgresIntegrationTestBase) as
    // more test classes create accounts, which was observed to interact non-deterministically
    // with HeaderWriterFilter's commit-time header buffering on a large response body — using a
    // bounded-size response keeps this assertion about the CSRF cookie itself deterministic.
    MvcResult getResult =
        mockMvc
            .perform(get("/audit-log").with(user(admin.getId().toString()).roles("ADMINISTRATOR")))
            .andExpect(status().isOk())
            .andReturn();
    Cookie xsrfCookie = getResult.getResponse().getCookie("XSRF-TOKEN");
    assertThat(xsrfCookie)
        .as("CsrfCookieFilter should have written an XSRF-TOKEN cookie")
        .isNotNull();

    // No .with(csrf()) bypass here — this is the exact mechanism the real Angular frontend uses.
    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/deactivate")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(xsrfCookie)
                .header("X-XSRF-TOKEN", xsrfCookie.getValue()))
        .andExpect(status().isOk());
  }

  @Test
  void mutatingRequest_withoutTheXsrfHeader_isRejected() throws Exception {
    StaffAccount admin =
        testAccountFactory.createActiveAccount(
            "csrf-cookie-admin-2@dentalclinic.example",
            "irrelevant-password-1",
            Role.ADMINISTRATOR);
    StaffAccount target =
        testAccountFactory.createActiveAccount(
            "csrf-cookie-target-2@dentalclinic.example", "irrelevant-password-1", Role.RECEPTION);

    mockMvc
        .perform(
            post("/accounts/" + target.getId() + "/deactivate")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR")))
        .andExpect(status().isForbidden());
  }
}

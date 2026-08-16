package com.dentalclinic.auth.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T039d — repeated invalid MFA codes increment the same {@code failed_login_count} used for
 * password failures and trigger lockout at the same 5-attempt/15-minute threshold (FR-011a).
 */
class MfaFailureLockoutIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  @Test
  void fiveInvalidMfaCodes_locksAccount_evenAgainstAValidCodeAfterward() throws Exception {
    String email = "mfa-lockout-test@dentalclinic.example";
    String password = "correct-horse-battery";
    var account = testAccountFactory.createActiveAccount(email, password, Role.DOCTOR);
    String secret = testAccountFactory.enrollMfa(account);

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", "10.0.3.1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
            .andReturn();
    String preAuthToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.preAuthToken");

    for (int attempt = 1; attempt <= 4; attempt++) {
      mockMvc
          .perform(
              post("/auth/mfa/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"preAuthToken\":\"%s\",\"totpCode\":\"000000\"}".formatted(preAuthToken)))
          .andExpect(status().isUnauthorized());
    }

    // 5th failure crosses the threshold.
    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"preAuthToken\":\"%s\",\"totpCode\":\"000000\"}".formatted(preAuthToken)))
        .andExpect(status().isLocked());

    // Even the correct code is now refused while locked.
    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"preAuthToken\":\"%s\",\"totpCode\":\"%s\"}"
                        .formatted(preAuthToken, testAccountFactory.currentTotpCode(secret))))
        .andExpect(status().isLocked());
  }
}

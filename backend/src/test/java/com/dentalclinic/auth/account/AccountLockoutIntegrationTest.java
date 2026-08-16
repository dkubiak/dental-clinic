package com.dentalclinic.auth.account;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** T035 — 5 consecutive failed attempts → 15-minute lockout (FR-011). */
class AccountLockoutIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  @Test
  void fiveFailedPasswordAttempts_locksAccount_evenAgainstTheCorrectPasswordAfterward()
      throws Exception {
    String email = "lockout-test@dentalclinic.example";
    String correctPassword = "correct-horse-battery";
    testAccountFactory.createActiveAccount(email, correctPassword, Role.RECEPTION);

    for (int attempt = 1; attempt <= 4; attempt++) {
      mockMvc
          .perform(
              post("/auth/login")
                  .header("X-Forwarded-For", "10.0.1.1")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      "{\"email\":\"%s\",\"password\":\"definitely-wrong-pw\"}".formatted(email)))
          .andExpect(status().isUnauthorized());
    }

    // 5th failure crosses the threshold.
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", "10.0.1.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"definitely-wrong-pw\"}".formatted(email)))
        .andExpect(status().isLocked());

    // Even the correct password is now refused while locked.
    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", "10.0.1.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, correctPassword)))
        .andExpect(status().isLocked());
  }
}

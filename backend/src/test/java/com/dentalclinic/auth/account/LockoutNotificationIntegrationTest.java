package com.dentalclinic.auth.account;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * T039f — account lockout (FR-011) triggers an email notification to the account's registered
 * address (FR-011c, T041c).
 */
class LockoutNotificationIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  @Test
  void accountLockout_sendsEmailToRegisteredAddress() throws Exception {
    String email = "lockout-notification-test@dentalclinic.example";
    testAccountFactory.createActiveAccount(email, "correct-horse-battery", Role.RECEPTION);

    for (int attempt = 1; attempt <= 5; attempt++) {
      mockMvc.perform(
          post("/auth/login")
              .header("X-Forwarded-For", "10.0.7.1")
              .contentType(MediaType.APPLICATION_JSON)
              .content("{\"email\":\"%s\",\"password\":\"definitely-wrong-pw\"}".formatted(email)));
    }

    mockMvc
        .perform(
            post("/auth/login")
                .header("X-Forwarded-For", "10.0.7.1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"%s\",\"password\":\"definitely-wrong-pw\"}".formatted(email)))
        .andExpect(status().isLocked());

    verify(emailSender).send(eq(email), anyString(), anyString());
  }
}

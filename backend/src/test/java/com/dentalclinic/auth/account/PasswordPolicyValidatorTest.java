package com.dentalclinic.auth.account;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.passwordreset.PasswordResetService;
import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/**
 * T039c — password creation/reset rejects passwords shorter than 12 characters and passwords found
 * on the breached-password list (FR-002a), exercised end-to-end through {@code POST
 * /auth/password-reset/confirm}.
 */
class PasswordPolicyValidatorTest extends PostgresIntegrationTestBase {

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");

  @Autowired private TestAccountFactory testAccountFactory;
  @Autowired private PasswordResetService passwordResetService;

  @Test
  void tooShortPassword_rejected() throws Exception {
    String rawToken = requestResetAndCaptureToken("short-pw-test@dentalclinic.example");

    // 10 characters — genuinely under the 12-character minimum (FR-002a).
    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"%s\",\"newPassword\":\"short12345\"}".formatted(rawToken)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void breachedPassword_rejectedEvenIfLongEnough() throws Exception {
    String rawToken = requestResetAndCaptureToken("breached-pw-test@dentalclinic.example");

    // "welcome123456" is on breached-passwords.txt and is 13 characters — length alone would
    // pass, so this proves the breach-list check runs independently of length.
    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"%s\",\"newPassword\":\"welcome123456\"}".formatted(rawToken)))
        .andExpect(status().isBadRequest());
  }

  @Test
  void compliantPassword_accepted() throws Exception {
    String rawToken = requestResetAndCaptureToken("compliant-pw-test@dentalclinic.example");

    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"token\":\"%s\",\"newPassword\":\"correct-horse-battery-staple\"}"
                        .formatted(rawToken)))
        .andExpect(status().isOk());
  }

  private String requestResetAndCaptureToken(String email) {
    testAccountFactory.createActiveAccount(email, "original-password-123", Role.RECEPTION);
    passwordResetService.requestReset(email);

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(eq(email), anyString(), bodyCaptor.capture());

    Matcher matcher = TOKEN_PATTERN.matcher(bodyCaptor.getValue());
    if (!matcher.find()) {
      throw new IllegalStateException(
          "Could not find reset token in email body: " + bodyCaptor.getValue());
    }
    return matcher.group(1);
  }
}

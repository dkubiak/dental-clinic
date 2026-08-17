package com.dentalclinic.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import com.jayway.jsonpath.JsonPath;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

/**
 * T070 — a newly admin-created account can complete first-login MFA enrollment and then log in with
 * role-appropriate access, all within the same flow (US3 AC1; quickstart.md Scenario 3 step 1):
 * admin creates the account, the emailed "set your password" link (reusing PasswordResetService,
 * see AccountAdminService#create) is followed, and the account logs in for the first time,
 * enrolling MFA as it goes (AuthService/MfaService — same mechanism as any other first login).
 */
class AccountLifecycleIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  private static final Pattern TOKEN_PATTERN = Pattern.compile("token=([\\w-]+)");

  @Test
  void newlyCreatedAccount_setsPassword_completesMfaEnrollment_andLogsInWithItsRole()
      throws Exception {
    StaffAccount admin =
        testAccountFactory.createActiveAccount(
            "lifecycle-admin@dentalclinic.example", "irrelevant-password-1", Role.ADMINISTRATOR);
    String newStaffEmail = "lifecycle-new-staff@dentalclinic.example";

    mockMvc
        .perform(
            post("/accounts")
                .with(user(admin.getId().toString()).roles("ADMINISTRATOR"))
                .cookie(CSRF_TOKEN_COOKIE)
                .header("X-XSRF-TOKEN", CSRF_TOKEN_VALUE)
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"role\":\"RECEPTION\"}".formatted(newStaffEmail)))
        .andExpect(status().isCreated());

    ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
    verify(emailSender).send(eq(newStaffEmail), anyString(), bodyCaptor.capture());
    Matcher matcher = TOKEN_PATTERN.matcher(bodyCaptor.getValue());
    assertThat(matcher.find()).isTrue();
    String resetToken = matcher.group(1);

    String newPassword = "lifecycle-new-password-1";
    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"token\":\"%s\",\"newPassword\":\"%s\"}".formatted(resetToken, newPassword)))
        .andExpect(status().isOk());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", "10.40.50.61")
                    .contentType(APPLICATION_JSON)
                    .content(
                        "{\"email\":\"%s\",\"password\":\"%s\"}"
                            .formatted(newStaffEmail, newPassword)))
            .andExpect(status().isOk())
            .andReturn();
    String preAuthToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.preAuthToken");
    String mfaSecret = JsonPath.read(loginResult.getResponse().getContentAsString(), "$.mfaSecret");
    assertThat(mfaSecret).isNotBlank(); // first login — enrollment setup info must be present

    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(APPLICATION_JSON)
                .content(
                    "{\"preAuthToken\":\"%s\",\"totpCode\":\"%s\"}"
                        .formatted(preAuthToken, testAccountFactory.currentTotpCode(mfaSecret))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("RECEPTION"))
        .andExpect(cookie().exists("SESSION"));
  }
}

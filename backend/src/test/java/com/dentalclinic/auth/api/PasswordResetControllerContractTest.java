package com.dentalclinic.auth.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.PasswordPolicyException;
import com.dentalclinic.auth.passwordreset.PasswordResetService;
import com.dentalclinic.auth.passwordreset.PasswordResetTokenInvalidException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T034 — contract test for {@code POST /auth/password-reset/request} and {@code /confirm} per
 * contracts/auth-api.yaml.
 */
@WebMvcTest(controllers = PasswordResetController.class)
@AutoConfigureMockMvc(addFilters = false)
class PasswordResetControllerContractTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private PasswordResetService passwordResetService;

  @Test
  void requestWithKnownEmail_returns202() throws Exception {
    mockMvc
        .perform(
            post("/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"doctor@dentalclinic.example"}
                    """))
        .andExpect(status().isAccepted());

    verify(passwordResetService).requestReset("doctor@dentalclinic.example");
  }

  @Test
  void requestWithUnknownEmail_alsoReturns202_uniformResponse() throws Exception {
    // Service never signals a match either way (FR-005/FR-016) — the controller always
    // returns 202 regardless of whether requestReset() found an account.
    mockMvc
        .perform(
            post("/auth/password-reset/request")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"nobody@dentalclinic.example"}
                    """))
        .andExpect(status().isAccepted());
  }

  @Test
  void confirmWithValidToken_returns200() throws Exception {
    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"raw-token-value","newPassword":"a-new-strong-password"}
                    """))
        .andExpect(status().isOk());
  }

  @Test
  void confirmWithExpiredOrUsedToken_returns410() throws Exception {
    doThrow(new PasswordResetTokenInvalidException())
        .when(passwordResetService)
        .confirmReset(anyString(), anyString());

    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"expired-token","newPassword":"a-new-strong-password"}
                    """))
        .andExpect(status().isGone());
  }

  @Test
  void confirmWithPolicyViolatingPassword_returns400() throws Exception {
    doThrow(new PasswordPolicyException("too weak"))
        .when(passwordResetService)
        .confirmReset(anyString(), anyString());

    mockMvc
        .perform(
            post("/auth/password-reset/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"token":"raw-token-value","newPassword":"password12345"}
                    """))
        .andExpect(status().isBadRequest());
  }
}

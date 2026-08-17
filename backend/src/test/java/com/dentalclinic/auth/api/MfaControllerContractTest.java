package com.dentalclinic.auth.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.AuthService;
import com.dentalclinic.auth.account.InvalidMfaCodeException;
import com.dentalclinic.auth.account.InvalidPreAuthTokenException;
import com.dentalclinic.auth.account.MfaVerificationResult;
import com.dentalclinic.auth.role.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T033 — contract test for {@code POST /auth/mfa/verify} per contracts/auth-api.yaml:
 * valid/invalid/expired pre-auth token. AuthService and SessionEstablisher are mocked.
 */
@WebMvcTest(controllers = MfaController.class)
@AutoConfigureMockMvc(addFilters = false)
class MfaControllerContractTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AuthService authService;
  @MockitoBean private SessionEstablisher sessionEstablisher;

  @Test
  void validCodeAndToken_returns200WithRole() throws Exception {
    when(authService.completeMfaLogin(anyString(), anyString()))
        .thenReturn(new MfaVerificationResult(UUID.randomUUID(), Role.RECEPTION));

    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"preAuthToken":"some-token","totpCode":"123456"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.role").value("RECEPTION"));
  }

  @Test
  void invalidCode_returns401() throws Exception {
    when(authService.completeMfaLogin(anyString(), anyString()))
        .thenThrow(new InvalidMfaCodeException());

    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"preAuthToken":"some-token","totpCode":"000000"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void expiredOrTamperedToken_returns401() throws Exception {
    when(authService.completeMfaLogin(anyString(), anyString()))
        .thenThrow(new InvalidPreAuthTokenException());

    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"preAuthToken":"expired-token","totpCode":"123456"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void malformedCode_failsValidation_returns400() throws Exception {
    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"preAuthToken":"some-token","totpCode":"abc"}
                    """))
        .andExpect(status().isBadRequest());
  }
}

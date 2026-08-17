package com.dentalclinic.auth.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.account.AccountDeactivatedException;
import com.dentalclinic.auth.account.AccountLockedException;
import com.dentalclinic.auth.account.AuthService;
import com.dentalclinic.auth.account.InvalidCredentialsException;
import com.dentalclinic.auth.account.LoginResult;
import com.dentalclinic.auth.account.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T032 — contract test for {@code POST /auth/login} per contracts/auth-api.yaml: valid, wrong
 * password, locked, deactivated. AuthService is mocked — this test verifies the controller's
 * request/response contract and status-code mapping (GlobalExceptionHandler, T028), not business
 * logic (covered by AccountLockoutIntegrationTest etc.). Security filters are disabled ({@code
 * addFilters = false}) so this stays a pure MVC-layer contract test; the real filter chain
 * (permitAll on this endpoint, CSRF, sessions) is exercised by the Testcontainers integration tests
 * instead.
 */
@WebMvcTest(controllers = LoginController.class)
@AutoConfigureMockMvc(addFilters = false)
class LoginControllerContractTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AuthService authService;

  @Test
  void validCredentials_returns200WithPreAuthToken() throws Exception {
    when(authService.login(anyString(), anyString(), anyString()))
        .thenReturn(new LoginResult("pre-auth-token-value", null, null));

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"reception@dentalclinic.example","password":"correct-horse-battery"}
                    """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.preAuthToken").value("pre-auth-token-value"))
        .andExpect(jsonPath("$.mfaRequired").value(true));
  }

  @Test
  void wrongPassword_returns401() throws Exception {
    when(authService.login(anyString(), anyString(), anyString()))
        .thenThrow(new InvalidCredentialsException());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"reception@dentalclinic.example","password":"wrong-password12"}
                    """))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void lockedAccount_returns423() throws Exception {
    when(authService.login(anyString(), anyString(), anyString()))
        .thenThrow(new AccountLockedException());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"reception@dentalclinic.example","password":"correct-horse-battery"}
                    """))
        .andExpect(status().isLocked());
  }

  @Test
  void deactivatedAccount_returns403() throws Exception {
    when(authService.login(anyString(), anyString(), anyString()))
        .thenThrow(new AccountDeactivatedException());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"reception@dentalclinic.example","password":"correct-horse-battery"}
                    """))
        .andExpect(status().isForbidden());
  }

  @Test
  void rateLimitedIp_returns429() throws Exception {
    when(authService.login(anyString(), anyString(), anyString()))
        .thenThrow(new RateLimitExceededException());

    mockMvc
        .perform(
            post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"email":"reception@dentalclinic.example","password":"correct-horse-battery"}
                    """))
        .andExpect(status().isTooManyRequests());
  }

  @Test
  void malformedRequest_missingFields_returns400() throws Exception {
    mockMvc
        .perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest());
  }
}

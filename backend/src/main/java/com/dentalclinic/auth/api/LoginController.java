package com.dentalclinic.auth.api;

import com.dentalclinic.auth.account.AuthService;
import com.dentalclinic.auth.account.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** T044 — {@code POST /auth/login}, {@code POST /auth/logout} per contracts/auth-api.yaml. */
@RestController
public class LoginController {

  private final AuthService authService;

  public LoginController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/auth/login")
  public ResponseEntity<LoginResponse> login(
      @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    LoginResult result =
        authService.login(request.email(), request.password(), clientIp(httpRequest));
    return ResponseEntity.ok(
        new LoginResponse(result.preAuthToken(), true, result.mfaSecret(), result.mfaOtpAuthUri()));
  }

  @PostMapping("/auth/logout")
  public ResponseEntity<Void> logout(HttpServletRequest httpRequest) {
    var session = httpRequest.getSession(false);
    if (session != null) {
      session.invalidate();
    }
    return ResponseEntity.noContent().build();
  }

  /** Prefers {@code X-Forwarded-For} (set by the ALB, research.md/plan.md AWS deployment). */
  private static String clientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}

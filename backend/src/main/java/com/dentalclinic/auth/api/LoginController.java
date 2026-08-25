package com.dentalclinic.auth.api;

import com.dentalclinic.auth.account.AuthService;
import com.dentalclinic.auth.account.LoginResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * T044 — {@code POST /auth/login}, {@code POST /auth/logout}, {@code GET /auth/session} per
 * contracts/auth-api.yaml.
 */
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

  /**
   * Lets the frontend rehydrate {@code AuthState.currentRole} from a still-valid session after a
   * full page reload/deep link, where the in-memory role set right after login/MFA is otherwise
   * lost (discovered as a live gap during 002-patient-records' quickstart validation, T063). No
   * request body needed — {@code Authentication} is already resolved by the security filter chain
   * from the SESSION cookie; an invalid/missing session never reaches this method at all ({@code
   * .anyRequest().authenticated()}, SecurityConfig), so no explicit 401 branch exists here.
   */
  @GetMapping("/auth/session")
  public ResponseEntity<SessionInfoResponse> session(Authentication authentication) {
    String role =
        authentication.getAuthorities().stream()
            .findFirst()
            .map(GrantedAuthority::getAuthority)
            .map(authority -> authority.replaceFirst("^ROLE_", ""))
            .orElseThrow();
    return ResponseEntity.ok(new SessionInfoResponse(role));
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

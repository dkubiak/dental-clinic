package com.dentalclinic.auth.api;

import com.dentalclinic.auth.account.AuthService;
import com.dentalclinic.auth.account.MfaVerificationResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** T045 — {@code POST /auth/mfa/verify} per contracts/auth-api.yaml. */
@RestController
public class MfaController {

  private final AuthService authService;
  private final SessionEstablisher sessionEstablisher;

  public MfaController(AuthService authService, SessionEstablisher sessionEstablisher) {
    this.authService = authService;
    this.sessionEstablisher = sessionEstablisher;
  }

  @PostMapping("/auth/mfa/verify")
  public ResponseEntity<MfaVerifyResponse> verify(
      @Valid @RequestBody MfaVerifyRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    MfaVerificationResult result =
        authService.completeMfaLogin(request.preAuthToken(), request.totpCode());
    sessionEstablisher.establish(httpRequest, httpResponse, result.accountId(), result.role());
    return ResponseEntity.ok(new MfaVerifyResponse(result.role()));
  }
}

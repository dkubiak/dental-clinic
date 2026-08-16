package com.dentalclinic.auth.api;

import com.dentalclinic.auth.passwordreset.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * T046 — {@code /auth/password-reset/request}, {@code /auth/password-reset/confirm} per
 * contracts/auth-api.yaml.
 */
@RestController
public class PasswordResetController {

  private final PasswordResetService passwordResetService;

  public PasswordResetController(PasswordResetService passwordResetService) {
    this.passwordResetService = passwordResetService;
  }

  @PostMapping("/auth/password-reset/request")
  public ResponseEntity<Void> request(@Valid @RequestBody PasswordResetRequestDto request) {
    // Always 202, regardless of whether the email matches an account (FR-005/FR-016) — the
    // service call below never signals a match either way.
    passwordResetService.requestReset(request.email());
    return ResponseEntity.accepted().build();
  }

  @PostMapping("/auth/password-reset/confirm")
  public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmDto request) {
    passwordResetService.confirmReset(request.token(), request.newPassword());
    return ResponseEntity.ok().build();
  }
}

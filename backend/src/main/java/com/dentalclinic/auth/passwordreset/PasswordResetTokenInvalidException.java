package com.dentalclinic.auth.passwordreset;

/** Reset token unknown, already used, or expired (FR-016) — maps to 410. */
public class PasswordResetTokenInvalidException extends RuntimeException {

  public PasswordResetTokenInvalidException() {
    super("Password reset token is invalid or has expired");
  }
}

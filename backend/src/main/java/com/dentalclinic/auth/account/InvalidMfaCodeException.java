package com.dentalclinic.auth.account;

/** The submitted TOTP code did not verify against the account's enrollment — maps to 401. */
public class InvalidMfaCodeException extends RuntimeException {

  public InvalidMfaCodeException() {
    super("Invalid MFA code");
  }
}

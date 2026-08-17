package com.dentalclinic.auth.account;

/** The pre-auth token is missing, malformed, tampered with, or expired (FR-015a) — maps to 401. */
public class InvalidPreAuthTokenException extends RuntimeException {

  public InvalidPreAuthTokenException() {
    super("Invalid or expired pre-auth token");
  }
}

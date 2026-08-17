package com.dentalclinic.auth.account;

/**
 * Wrong email or wrong password — deliberately the same exception/response for both cases so the
 * caller cannot tell which was wrong (spec.md Acceptance Scenario US1-4).
 */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("Invalid email or password");
  }
}

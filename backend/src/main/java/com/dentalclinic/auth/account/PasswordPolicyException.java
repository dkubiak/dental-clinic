package com.dentalclinic.auth.account;

/** Thrown by {@link PasswordPolicyValidator} when a candidate password violates FR-002a. */
public class PasswordPolicyException extends RuntimeException {

  public PasswordPolicyException(String message) {
    super(message);
  }
}

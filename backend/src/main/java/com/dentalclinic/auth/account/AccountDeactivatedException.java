package com.dentalclinic.auth.account;

/** Account is deactivated and cannot authenticate (FR-010) — maps to 403. */
public class AccountDeactivatedException extends RuntimeException {

  public AccountDeactivatedException() {
    super("Account is deactivated");
  }
}

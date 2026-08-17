package com.dentalclinic.auth.account;

/**
 * Account temporarily locked after 5 consecutive failed attempts (FR-011, FR-011a) — maps to 423.
 */
public class AccountLockedException extends RuntimeException {

  public AccountLockedException() {
    super("Account is temporarily locked");
  }
}

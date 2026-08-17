package com.dentalclinic.auth.account;

/**
 * The target account id in an admin-only account-management request does not exist. Maps to the
 * same generic 404 body as an RBAC denial (GlobalExceptionHandler) — contracts/auth-api.yaml
 * deliberately groups "caller lacks ADMINISTRATOR role" and "account does not exist" under one 404
 * description, consistent with FR-005's "never reveal whether a resource exists".
 */
public class AccountNotFoundException extends RuntimeException {

  public AccountNotFoundException() {
    super("Account not found");
  }
}

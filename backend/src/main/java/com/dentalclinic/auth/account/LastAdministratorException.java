package com.dentalclinic.auth.account;

/**
 * The target of a deactivate request is the last remaining active {@code ADMINISTRATOR} account
 * (FR-009a) — maps to 409. The rejected attempt is still audit-logged by the caller
 * (AccountAdminService) before this is thrown.
 */
public class LastAdministratorException extends RuntimeException {

  public LastAdministratorException() {
    super("Cannot deactivate the last remaining active administrator account");
  }
}

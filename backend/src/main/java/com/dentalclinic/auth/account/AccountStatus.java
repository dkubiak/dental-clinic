package com.dentalclinic.auth.account;

/**
 * StaffAccount lifecycle status (FR-009/FR-010). No "deleted" status exists — see spec.md Edge
 * Cases.
 */
public enum AccountStatus {
  ACTIVE,
  DEACTIVATED
}

package com.dentalclinic.auth.account;

import com.dentalclinic.auth.role.Role;
import java.util.UUID;

/** Result of a successful MFA verification (step 2 of login) — see {@link AuthService}. */
public record MfaVerificationResult(UUID accountId, Role role) {}

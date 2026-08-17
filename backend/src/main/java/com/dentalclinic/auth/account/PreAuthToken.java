package com.dentalclinic.auth.account;

import java.time.Instant;
import java.util.UUID;

/** A verified pre-auth token's decoded payload (see {@link PreAuthTokenService}). */
public record PreAuthToken(UUID accountId, Instant expiresAt) {}

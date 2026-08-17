package com.dentalclinic.auth.account;

/** Source IP exceeded the per-IP login-attempt rate limit (FR-011b) — maps to 429. */
public class RateLimitExceededException extends RuntimeException {

  public RateLimitExceededException() {
    super("Too many login attempts from this source");
  }
}

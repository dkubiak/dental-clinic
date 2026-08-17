package com.dentalclinic.auth.account;

/**
 * Result of a successful password step ({@link AuthService#login}). {@code mfaSecret}/{@code
 * mfaOtpAuthUri} are populated only the first time an account authenticates (its enrollment is not
 * yet finished — data-model.md) so the frontend can render enrollment (QR / manual-entry secret);
 * {@code null} for an already-enrolled account, which only needs the MFA challenge form.
 */
public record LoginResult(String preAuthToken, String mfaSecret, String mfaOtpAuthUri) {}

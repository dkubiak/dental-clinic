package com.dentalclinic.auth.api;

/**
 * Per contracts/auth-api.yaml. {@code mfaSecret}/{@code mfaOtpAuthUri} are additive fields beyond
 * the originally documented schema (see AuthService/LoginResult javadoc) — populated only when the
 * account has not yet finished MFA enrollment, so the frontend can render the QR/manual-entry setup
 * step; {@code null} otherwise.
 */
public record LoginResponse(
    String preAuthToken, boolean mfaRequired, String mfaSecret, String mfaOtpAuthUri) {}

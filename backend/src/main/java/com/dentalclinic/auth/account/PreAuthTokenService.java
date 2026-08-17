package com.dentalclinic.auth.account;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies the short-lived pre-auth token bridging {@code POST /auth/login} (password
 * step) and {@code POST /auth/mfa/verify} (MFA step) — FR-015a, exactly 5 minutes validity.
 *
 * <p>Deliberately NOT a new database table: data-model.md does not define a persisted entity for
 * this token, and plan.md's Constraints explicitly avoid introducing new state where a
 * self-contained value suffices. The token is a base64url-encoded {@code
 * accountId|expiresAtEpochMs} payload with an HMAC-SHA256 signature over that payload, so it cannot
 * be forged or tampered with without the server-held secret, and its expiry is verified without any
 * datastore lookup.
 *
 * <p>{@code clock} is injectable (defaulting to the system UTC clock) so expiry can be tested
 * deterministically (PreAuthTokenExpiryIntegrationTest, T039g) without waiting 5 real minutes.
 */
@Service
public class PreAuthTokenService {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final Duration VALIDITY = Duration.ofMinutes(5); // FR-015a — exactly 5 minutes

  private final SecretKeySpec signingKey;
  private final Clock clock;

  @Autowired
  public PreAuthTokenService(@Value("${app.security.pre-auth-token-secret}") String secret) {
    this(secret, Clock.systemUTC());
  }

  PreAuthTokenService(String secret, Clock clock) {
    this.signingKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
    this.clock = clock;
  }

  public String issue(UUID accountId) {
    Instant expiresAt = Instant.now(clock).plus(VALIDITY);
    String payload = accountId + "|" + expiresAt.toEpochMilli();
    String signature = sign(payload);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString((payload + "|" + signature).getBytes(StandardCharsets.UTF_8));
  }

  /** Returns the decoded token if it is well-formed, correctly signed, and not expired. */
  public Optional<PreAuthToken> verify(String token) {
    try {
      String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
      String[] parts = decoded.split("\\|", 3);
      if (parts.length != 3) {
        return Optional.empty();
      }
      String accountIdPart = parts[0];
      String expiresAtPart = parts[1];
      String signaturePart = parts[2];

      String expectedSignature = sign(accountIdPart + "|" + expiresAtPart);
      if (!constantTimeEquals(expectedSignature, signaturePart)) {
        return Optional.empty();
      }

      Instant expiresAt = Instant.ofEpochMilli(Long.parseLong(expiresAtPart));
      if (Instant.now(clock).isAfter(expiresAt)) {
        return Optional.empty();
      }

      return Optional.of(new PreAuthToken(UUID.fromString(accountIdPart), expiresAt));
    } catch (RuntimeException e) {
      // Malformed/tampered token — treated as invalid, never propagated as a server error.
      return Optional.empty();
    }
  }

  private String sign(String payload) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(signingKey);
      byte[] signatureBytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(signatureBytes);
    } catch (java.security.GeneralSecurityException e) {
      throw new IllegalStateException("Failed to sign pre-auth token", e);
    }
  }

  private static boolean constantTimeEquals(String a, String b) {
    return java.security.MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}

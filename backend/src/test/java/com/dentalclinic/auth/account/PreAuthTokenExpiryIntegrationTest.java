package com.dentalclinic.auth.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dentalclinic.auth.role.Role;
import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import com.dentalclinic.auth.support.TestAccountFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;

/**
 * T039g — the pre-auth token issued by {@code POST /auth/login} is rejected by {@code POST
 * /auth/mfa/verify} exactly 5 minutes after issuance (FR-015a). {@link PreAuthTokenService} takes
 * an injectable {@link Clock} (package-private constructor) specifically so expiry can be tested
 * deterministically here, without waiting 5 real minutes in an HTTP round trip.
 */
class PreAuthTokenExpiryIntegrationTest extends PostgresIntegrationTestBase {

  @Autowired private TestAccountFactory testAccountFactory;

  @Value("${app.security.pre-auth-token-secret}")
  private String preAuthTokenSecret;

  @Test
  void tokenIsValidImmediatelyAfterIssue() {
    UUID accountId = UUID.randomUUID();
    PreAuthTokenService service = new PreAuthTokenService(preAuthTokenSecret, Clock.systemUTC());

    String token = service.issue(accountId);

    Optional<PreAuthToken> decoded = service.verify(token);
    assertThat(decoded).isPresent();
    assertThat(decoded.get().accountId()).isEqualTo(accountId);
  }

  @Test
  void tokenIsRejected_exactlyFiveMinutesAfterIssuance() {
    UUID accountId = UUID.randomUUID();
    Instant issuedAt = Instant.parse("2026-01-01T10:00:00Z");
    Clock issuanceClock = Clock.fixed(issuedAt, java.time.ZoneOffset.UTC);
    PreAuthTokenService issuingService = new PreAuthTokenService(preAuthTokenSecret, issuanceClock);
    String token = issuingService.issue(accountId);

    // Still valid one second before the 5-minute mark.
    PreAuthTokenService justBeforeExpiry =
        new PreAuthTokenService(
            preAuthTokenSecret,
            Clock.fixed(
                issuedAt.plus(Duration.ofMinutes(5)).minusSeconds(1), java.time.ZoneOffset.UTC));
    assertThat(justBeforeExpiry.verify(token)).isPresent();

    // Rejected once 5 minutes have elapsed.
    PreAuthTokenService afterExpiry =
        new PreAuthTokenService(
            preAuthTokenSecret,
            Clock.fixed(
                issuedAt.plus(Duration.ofMinutes(5)).plusSeconds(1), java.time.ZoneOffset.UTC));
    assertThat(afterExpiry.verify(token)).isEmpty();
  }

  @Test
  void expiredToken_rejectedByMfaVerifyEndpoint() throws Exception {
    String email = "pre-auth-expiry-test@dentalclinic.example";
    var account =
        testAccountFactory.createActiveAccount(email, "correct-horse-battery", Role.RECEPTION);
    testAccountFactory.enrollMfa(account);

    // A token minted 6 minutes ago is already expired relative to real "now" — equivalent to
    // waiting 6 real minutes after a genuine /auth/login call, without the test actually waiting.
    PreAuthTokenService staleIssuer =
        new PreAuthTokenService(
            preAuthTokenSecret, Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-6)));
    String expiredToken = staleIssuer.issue(account.getId());

    mockMvc
        .perform(
            post("/auth/mfa/verify")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"preAuthToken\":\"%s\",\"totpCode\":\"123456\"}".formatted(expiredToken)))
        .andExpect(status().isUnauthorized());
  }
}

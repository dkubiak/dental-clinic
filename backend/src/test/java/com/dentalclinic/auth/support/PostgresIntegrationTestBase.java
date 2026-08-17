package com.dentalclinic.auth.support;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.dentalclinic.auth.notification.EmailSender;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.DecryptRequest;
import software.amazon.awssdk.services.kms.model.DecryptResponse;
import software.amazon.awssdk.services.kms.model.EncryptRequest;
import software.amazon.awssdk.services.kms.model.EncryptResponse;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Shared base for every Testcontainers-backed integration test in Phase 3+ (research.md #9 — a real
 * PostgreSQL, never an in-memory substitute, so Flyway migrations and Postgres-specific behavior
 * like the audit log's grant restrictions are actually exercised).
 *
 * <p>Uses the Testcontainers "singleton container" pattern (one container for the whole JVM/test
 * run, started once in a static initializer, never explicitly stopped — Testcontainers' Ryuk reaper
 * cleans it up) rather than {@code @Testcontainers}/{@code @Container}'s per-class lifecycle,
 * because {@link #bootstrapAppRole()} below only needs to run once.
 *
 * <p>{@link #bootstrapAppRole()} exists because {@code V5__audit_log.sql} is what CREATEs the
 * {@code auth_service_app} role that {@code spring.datasource.username} defaults to (see
 * application.yml comment) — Flyway must run as a role that already exists and can create tables on
 * a brand-new database, so this pre-creates {@code auth_service_app} (with a password, unlike V5's
 * passwordless {@code CREATE ROLE IF NOT EXISTS}, which becomes a harmless no-op here) before
 * Spring Boot / Flyway ever starts. This mirrors how Terraform provisions the role out-of-band for
 * RDS/Aurora (V5's own comment).
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class PostgresIntegrationTestBase {

  @Autowired protected MockMvc mockMvc;

  // Real AWS calls are neither reachable nor desired in tests. KmsClient is stubbed as a
  // reversible passthrough (ciphertext == plaintext bytes) so MfaEnrollment round-trips through
  // TotpSecretConverter (T022a) without needing a real KMS key; EmailSender is stubbed as a no-op
  // so AccountLockoutService (T041c) / PasswordResetService (T043) don't fail mid-request — tests
  // that care about a specific email being sent (e.g. LockoutNotificationIntegrationTest, T039f)
  // verify against this same mock. SesClient is ALSO mocked directly (even though nothing in this
  // test context calls it once EmailSender is mocked) because Spring eagerly instantiates every
  // singleton @Bean in the context regardless of whether anything currently injects it —
  // AwsClientsConfig.sesClient()'s real factory method would otherwise still run and fail
  // resolving an AWS region.
  @MockitoBean protected KmsClient kmsClient;
  @MockitoBean protected SesClient sesClient;
  @MockitoBean protected EmailSender emailSender;

  @BeforeEach
  void stubAwsDependencies() {
    lenient()
        .when(kmsClient.encrypt(any(EncryptRequest.class)))
        .thenAnswer(
            invocation -> {
              EncryptRequest request = invocation.getArgument(0);
              return EncryptResponse.builder().ciphertextBlob(request.plaintext()).build();
            });
    lenient()
        .when(kmsClient.decrypt(any(DecryptRequest.class)))
        .thenAnswer(
            invocation -> {
              DecryptRequest request = invocation.getArgument(0);
              return DecryptResponse.builder().plaintext(request.ciphertextBlob()).build();
            });
  }

  protected static final String APP_ROLE_PASSWORD = "test-app-password";

  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("dental_clinic_auth");

  static {
    POSTGRES.start();
    bootstrapAppRole();
  }

  private static void bootstrapAppRole() {
    try (Connection connection =
            DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Statement statement = connection.createStatement()) {
      statement.execute(
          """
          DO $$
          BEGIN
              IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_service_app') THEN
                  CREATE ROLE auth_service_app WITH LOGIN PASSWORD '%s' CREATEDB;
              END IF;
          END
          $$;
          """
              .formatted(APP_ROLE_PASSWORD));
    } catch (SQLException e) {
      throw new IllegalStateException("Failed to bootstrap auth_service_app role for tests", e);
    }
  }

  @DynamicPropertySource
  static void datasourceProperties(DynamicPropertyRegistry registry) {
    // Flyway runs as the container's own admin user (owns/creates every table, V1 onward).
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);

    // The application itself connects as the least-privilege role V5 grants (FR-008).
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", () -> "auth_service_app");
    registry.add("spring.datasource.password", () -> APP_ROLE_PASSWORD);
  }

  /**
   * Drives the full two-step login (password + MFA) through the real HTTP endpoints for an already
   * MFA-enrolled account (see {@link TestAccountFactory#enrollMfa}) and returns the resulting
   * session cookie, for tests that need a genuinely established session rather than a synthetic
   * {@code SecurityMockMvcRequestPostProcessors.user(...)} principal (e.g. session idle-timeout /
   * hard-cap tests, which manipulate real session state). {@code totpCodeSupplier} is invoked only
   * after the password step succeeds, so it can read the freshly-issued pre-auth-token-triggered
   * enrollment secret if needed — most callers just pass {@code () ->
   * testAccountFactory.currentTotpCode(secret)}.
   *
   * <p>{@code sourceIp} MUST be unique per calling test class (not reused elsewhere): every test
   * class shares the same Postgres container/database (singleton pattern above), so the per-IP
   * rate-limit counter (FR-011b, {@code login_attempt_by_ip}) persists across test classes within a
   * test run — without a distinct IP per class, unrelated tests' login attempts would accumulate
   * against the same bucket and could trip 429 unexpectedly (see IpRateLimitIntegrationTest, which
   * deliberately reuses one IP across many requests).
   */
  protected Cookie loginAndGetSessionCookie(
      String email,
      String rawPassword,
      String sourceIp,
      java.util.function.Supplier<String> totpCodeSupplier)
      throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/auth/login")
                    .header("X-Forwarded-For", sourceIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, rawPassword)))
            .andReturn();
    String preAuthToken =
        JsonPath.read(loginResult.getResponse().getContentAsString(), "$.preAuthToken");

    MvcResult mfaResult =
        mockMvc
            .perform(
                post("/auth/mfa/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        "{\"preAuthToken\":\"%s\",\"totpCode\":\"%s\"}"
                            .formatted(preAuthToken, totpCodeSupplier.get())))
            .andReturn();

    Cookie sessionCookie = mfaResult.getResponse().getCookie("SESSION");
    if (sessionCookie == null) {
      throw new IllegalStateException(
          "No SESSION cookie returned — MFA verification did not succeed: "
              + mfaResult.getResponse().getContentAsString());
    }
    return sessionCookie;
  }

  /** {@code spring_session.session_id} is the base64-decoded value of the SESSION cookie. */
  protected static String decodeSessionId(Cookie sessionCookie) {
    return new String(java.util.Base64.getDecoder().decode(sessionCookie.getValue()));
  }
}

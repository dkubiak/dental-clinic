package com.dentalclinic.auth.account;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * T041b — Postgres-backed sliding-window counter for per-IP login-attempt rate limiting (FR-011b,
 * data-model.md {@code LoginAttemptByIp}), checked/incremented in {@code POST /auth/login} before
 * credential verification. Works correctly across every auth-service replica because the counter
 * lives in the shared RDS/Aurora instance (plan.md "Distributed brute-force protection"), not in
 * per-pod memory — deliberately no new datastore is introduced.
 *
 * <p>Implemented with a raw {@link JdbcTemplate} upsert (rather than a JPA entity) so the increment
 * is a single atomic {@code INSERT ... ON CONFLICT DO UPDATE} round trip — the correctness property
 * this table exists for (no lost updates between concurrent replicas).
 */
@Service
public class IpRateLimitService {

  private static final Duration OLD_WINDOW_RETENTION = Duration.ofHours(1);
  // Opportunistic cleanup (data-model.md — "no long-term retention needed"): run on a small
  // fraction of calls rather than adding a DELETE to every single login attempt.
  private static final double CLEANUP_PROBABILITY = 0.01;

  private final JdbcTemplate jdbcTemplate;
  private final int maxAttemptsPerWindow;
  private final Duration windowDuration;

  public IpRateLimitService(
      JdbcTemplate jdbcTemplate,
      @Value("${app.ip-rate-limit.max-attempts-per-window}") int maxAttemptsPerWindow,
      @Value("${app.ip-rate-limit.window-duration}") Duration windowDuration) {
    this.jdbcTemplate = jdbcTemplate;
    this.maxAttemptsPerWindow = maxAttemptsPerWindow;
    this.windowDuration = windowDuration;
  }

  /**
   * Increments the counter for {@code ipAddress} in the current time bucket and reports whether the
   * request should be allowed to proceed. Independent of, and in addition to, any single account's
   * own lockout state (FR-011b).
   */
  @Transactional
  public boolean isAllowed(String ipAddress) {
    Instant windowStart = currentWindowStart();
    Integer attemptCount =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO login_attempt_by_ip (ip_address, window_start, attempt_count)
            VALUES (CAST(? AS inet), ?, 1)
            ON CONFLICT (ip_address, window_start)
            DO UPDATE SET attempt_count = login_attempt_by_ip.attempt_count + 1
            RETURNING attempt_count
            """,
            Integer.class,
            ipAddress,
            Timestamp.from(windowStart));

    if (Math.random() < CLEANUP_PROBABILITY) {
      jdbcTemplate.update(
          "DELETE FROM login_attempt_by_ip WHERE window_start < ?",
          Timestamp.from(windowStart.minus(OLD_WINDOW_RETENTION)));
    }

    return attemptCount != null && attemptCount <= maxAttemptsPerWindow;
  }

  private Instant currentWindowStart() {
    long windowSeconds = windowDuration.getSeconds();
    long epochSeconds = Instant.now().getEpochSecond();
    long bucketStartSeconds = epochSeconds - (epochSeconds % windowSeconds);
    return Instant.ofEpochSecond(bucketStartSeconds);
  }
}

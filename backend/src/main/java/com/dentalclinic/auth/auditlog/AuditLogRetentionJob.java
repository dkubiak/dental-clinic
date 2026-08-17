package com.dentalclinic.auth.auditlog;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * T085a — FR-018: purges {@link AuditLogEntry} rows older than 3 years, running under the
 * privileged {@code auth_service_retention} DB role (V8__audit_log_retention_role.sql), separate
 * from the application's normal {@code auth_service_app} role, which has DELETE revoked on this
 * table (V5__audit_log.sql, FR-008) — this scheduled job is the one sanctioned exception to "no
 * delete path", and it is itself logged only via SLF4J (ops/job-run logs), never back into the
 * audit log it purges — data-model.md's Retention note: logging a purge inside the log being purged
 * would be circular.
 *
 * <p>The retention connection is built directly here (a plain Hikari pool, not a Spring-managed
 * {@code @Bean DataSource}) rather than as a second {@code DataSource} bean: registering ANY
 * additional {@code DataSource} bean trips Spring Boot's
 * {@code @ConditionalOnMissingBean(DataSource.class)} guard on {@code DataSourceAutoConfiguration},
 * silently disabling the properly-pooled, {@code spring.datasource.*}-configured main datasource
 * the rest of the application (JPA, the primary {@code JdbcTemplate}) relies on.
 */
@Component
public class AuditLogRetentionJob {

  private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionJob.class);

  private final JdbcTemplate retentionJdbcTemplate;
  private final Duration maxAge;

  public AuditLogRetentionJob(
      @Value("${app.retention.db.url}") String url,
      @Value("${app.retention.db.username}") String username,
      @Value("${app.retention.db.password}") String password,
      @Value("${app.retention.max-age}") Duration maxAge) {
    HikariDataSource retentionDataSource = new HikariDataSource();
    retentionDataSource.setJdbcUrl(url);
    retentionDataSource.setUsername(username);
    retentionDataSource.setPassword(password);
    this.retentionJdbcTemplate = new JdbcTemplate(retentionDataSource);
    this.maxAge = maxAge;
  }

  @Scheduled(cron = "${app.retention.cron}")
  public void purgeExpiredEntries() {
    Instant cutoff = Instant.now().minus(maxAge);
    int deleted =
        retentionJdbcTemplate.update(
            "DELETE FROM audit_log_entry WHERE occurred_at < ?", Timestamp.from(cutoff));
    log.info("Audit log retention: purged {} entries older than {} (FR-018)", deleted, cutoff);
  }
}

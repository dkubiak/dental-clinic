package com.dentalclinic.auth.auditlog;

import static org.assertj.core.api.Assertions.assertThat;

import com.dentalclinic.auth.support.PostgresIntegrationTestBase;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * research.md #5a (002-patient-records) — the audit log's hash chain must stay unbroken when
 * appended to by two independent writer <em>processes</em>, not just concurrent threads within one
 * JVM object. {@code synchronized} on {@link AuditLogWriter#append} only ever serialized calls made
 * through the same object's monitor — it could not have coordinated auth-service's own ≥2 replicas
 * with each other, nor a second service ({@code patient-service}) writing to the same table. This
 * test simulates that by constructing <strong>two separate</strong> {@link AuditLogWriter}
 * instances (mirroring two independent writer processes/services) sharing one Testcontainers
 * Postgres instance, firing many concurrent {@code append()} calls interleaved from both, and
 * asserting the resulting chain segment is unbroken.
 *
 * <p>Deliberately verifies only the entries <em>this test itself wrote</em> (by id, in ascending
 * order) rather than {@link AuditHashChainVerifier#verifyAll()} against the whole table: {@link
 * PostgresIntegrationTestBase} shares one Postgres instance/table across every test class in the
 * run, and {@code AuditLogRetentionJobTest} legitimately deletes an old row from earlier in that
 * shared chain — which would make a whole-table verification report a break that has nothing to do
 * with this test's own writer-concurrency behavior. Re-implementing the linkage/content check
 * inline (using the same package-private {@link AuditEntryHash#compute} {@link
 * AuditHashChainVerifier} itself uses) keeps this test isolated from that shared, cross-class table
 * state.
 */
class AuditLogWriterConcurrencyTest extends PostgresIntegrationTestBase {

  @Autowired private AuditLogEntryRepository repository;
  @Autowired private DataSource dataSource;
  @Autowired private PlatformTransactionManager transactionManager;

  @Test
  void twoIndependentWriters_concurrentAppends_chainSegmentStaysUnbroken() throws Exception {
    // Two separate objects — NOT the same Spring-managed singleton bean — simulating two
    // independent writer processes (today: auth-service's own replicas; from 002-patient-records
    // onward: also patient-service) sharing this table.
    AuditLogWriter writerA = new AuditLogWriter(repository, dataSource, transactionManager);
    AuditLogWriter writerB = new AuditLogWriter(repository, dataSource, transactionManager);

    int callsPerWriter = 15;
    ExecutorService executor = Executors.newFixedThreadPool(2 * callsPerWriter);
    CountDownLatch readyLatch = new CountDownLatch(2 * callsPerWriter);
    CountDownLatch startLatch = new CountDownLatch(1);
    List<AuditLogEntry> written;
    try {
      List<Future<AuditLogEntry>> futures = new ArrayList<>();
      for (int i = 0; i < callsPerWriter; i++) {
        futures.add(executor.submit(appendTask(writerA, readyLatch, startLatch)));
        futures.add(executor.submit(appendTask(writerB, readyLatch, startLatch)));
      }

      readyLatch.await(10, TimeUnit.SECONDS);
      startLatch.countDown();

      written = new ArrayList<>();
      for (Future<AuditLogEntry> future : futures) {
        written.add(future.get(30, TimeUnit.SECONDS));
      }
    } finally {
      executor.shutdownNow();
    }

    written.sort(Comparator.comparing(AuditLogEntry::getId));

    for (int i = 0; i < written.size(); i++) {
      AuditLogEntry entry = written.get(i);
      if (i > 0) {
        assertThat(entry.getPreviousEntryHash())
            .as("entry %d's previous_entry_hash must equal entry %d's entry_hash", i, i - 1)
            .isEqualTo(written.get(i - 1).getEntryHash());
      }
      String recomputed =
          AuditEntryHash.compute(
              entry.getPreviousEntryHash(),
              entry.getEventType(),
              entry.getActorAccountId(),
              entry.getTargetAccountId(),
              entry.getOccurredAt(),
              entry.getBeforeState(),
              entry.getAfterState());
      assertThat(recomputed)
          .as("entry %d's stored entry_hash must match its own content", i)
          .isEqualTo(entry.getEntryHash());
    }
  }

  private Callable<AuditLogEntry> appendTask(
      AuditLogWriter writer, CountDownLatch ready, CountDownLatch start) {
    return () -> {
      ready.countDown();
      start.await(10, TimeUnit.SECONDS);
      // actor/target left null (both columns are nullable — e.g. an unauthenticated failed
      // login has no actor either) to keep this test focused purely on hash-chain concurrency,
      // without needing a real staff_account row to satisfy the FK constraint.
      return writer.append(AuditEventType.LOGIN_SUCCESS, null, null, null, null, null);
    };
  }
}
